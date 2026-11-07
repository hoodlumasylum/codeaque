"""Local-only AI helper for Linux PC CodeAque. No online AI service or keys."""
from __future__ import annotations

import ctypes
import ctypes.util
import json
import os
import platform
import shutil
import subprocess
import tarfile
import tempfile
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from . import launcher, paths, updater

MODEL = "qwen2.5:1.5b"
MODEL_LABEL = "Qwen2.5 1.5B Instruct Q4_K_M (986 MB, Apache 2.0)"
MODEL_BYTES = 986048512
RUNTIME_BYTES = {"x86_64": 1543547172, "aarch64": 1600157086}


class AskError(Exception):
    pass


def runtime_url(machine=None):
    arch = machine or platform.machine()
    if arch in ("amd64", "x86_64"):
        return "https://ollama.com/download/ollama-linux-amd64.tar.zst"
    if arch in ("arm64", "aarch64"):
        return "https://ollama.com/download/ollama-linux-arm64.tar.zst"
    return ""


def download_size(machine=None):
    arch = machine or platform.machine()
    total = RUNTIME_BYTES.get(arch, 1600157086) + MODEL_BYTES
    return f"about {total / 1e9:.1f} GB to download; 7 GB of free disk for unpacking"


def install_dir():
    return paths.data_dir() / "local-ai"


def executable(root=None):
    exe = Path(root or install_dir()) / "runtime/bin/ollama"
    if exe.is_file() and os.access(exe, os.X_OK):
        return str(exe)
    return shutil.which("ollama") or ""


def memory_bytes():
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemAvailable:"):
                return int(line.split()[1]) * 1024
    except (OSError, ValueError, IndexError):
        pass
    return 0


def preflight(root=None):
    if not runtime_url():
        return False, "Setup is offered for Linux x86-64 or ARM64. Nothing was downloaded."
    free_memory = memory_bytes()
    if free_memory and free_memory < 8 * 1024**3:
        return False, "Less than 8 GB RAM is available. It is recommended to skip Ask CodeAque; close other apps and try again only when at least 8 GB is free. Nothing was downloaded."
    free_disk = shutil.disk_usage(root or paths.data_dir()).free
    if free_disk < 7 * 1024**3:
        return False, f"Setup requires 7 GB free disk; you have {free_disk / 1024**3:.1f} GB. Nothing was downloaded."
    if not ctypes.util.find_library("zstd"):
        return False, "The Linux zstd library for unpacking Ollama is unavailable. Nothing was downloaded."
    return True, ""


def request_host():
    host = os.environ.get("ATARI_ASK_HOST", "127.0.0.1:11434")
    if "://" not in host:
        host = "http://" + host
    parts = urlsplit(host)
    try:
        port = parts.port
    except ValueError as exc:
        raise AskError("The local helper address is invalid. No question was sent.") from exc
    if parts.scheme != "http" or parts.hostname != "127.0.0.1" or parts.username or parts.password:
        raise AskError("Ask CodeAque only connects to the loopback helper on this computer. No question was sent.")
    return "http://127.0.0.1" + (f":{port}" if port is not None else "") + "/api"


def local_models():
    request = Request(request_host() + "/tags", headers={"Accept": "application/json"})
    with urlopen(request, timeout=3) as response:
        return json.loads(response.read(2_000_000)).get("models", [])


def model_installed(client=local_models):
    return any(m.get("name", "").split(":") == MODEL.split(":") for m in client())


def status():
    if not executable():
        return "Not set up yet. Press SET UP LOCAL AI for one-time downloads. Internet is only needed for setup; afterward chat stays offline. " + download_size() + ".\n\nModel: " + MODEL_LABEL + ". No API key, account, terminal or administrator password."
    try:
        if not model_installed():
            return "The local 986 MB model is missing. Press SET UP LOCAL AI to fetch it, then use the chat screen offline."
        return "Ready: local Qwen2.5 answers offline. No account or key and no per-question cost. Chat is kept in memory only, not saved to disk."
    except Exception:
        return "Ollama has not started yet. Ask a question and CodeAque will start it, or retry setup. Questions are sent only to this computer."


def child_env(root=None):
    env = launcher.clean_env()
    env["OLLAMA_HOST"] = request_host().rsplit("/api", 1)[0]
    env["OLLAMA_MODELS"] = str((root or install_dir()) / "models")
    env["OLLAMA_NO_CLOUD"] = "1"
    return env


def start_server(root=None, cancel=None, delay=.1):
    try:
        local_models()
        return None  # Never stop a helper not started by this session.
    except AskError:
        raise
    except Exception:
        pass
    if not executable(root):
        raise AskError("Ask CodeAque is not set up. Choose SET UP LOCAL AI for the one-time download. Your question was not sent.")
    try:
        proc = subprocess.Popen([executable(root), "serve"], env=child_env(root), stdin=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    except OSError as exc:
        raise AskError("The local helper could not start. Retry local setup. Chat was not sent.") from exc
    for _ in range(200):
        if cancel is not None and cancel.is_set():
            stop(proc)
            raise updater.Cancelled()
        if proc.poll() is not None:
            raise AskError("Ollama stopped while starting. Check available memory or retry setup. No chat was sent.")
        try:
            local_models()
            return proc
        except AskError:
            raise
        except Exception:
            time.sleep(delay)
    stop(proc)
    raise AskError("The local helper has not responded yet. Retry; nothing was sent outside this PC.")


def stop(proc):
    if proc is None:
        return
    try:
        proc.terminate()
        proc.wait(timeout=5)
    except Exception:
        try:
            proc.kill()
            proc.wait(timeout=2)
        except Exception:
            pass


def _unzstd(source, output):
    """Use Mint's libzstd directly; no root package installation is required."""
    lib = ctypes.CDLL(ctypes.util.find_library("zstd") or "libzstd.so.1")
    ptr = ctypes.c_void_p
    size_t = ctypes.c_size_t

    class Buffer(ctypes.Structure):
        _fields_ = [("src", ptr), ("size", size_t), ("pos", size_t)]

    lib.ZSTD_createDStream.argtypes = []
    lib.ZSTD_createDStream.restype = ptr
    lib.ZSTD_initDStream.argtypes = [ptr]
    lib.ZSTD_initDStream.restype = size_t
    lib.ZSTD_isError.argtypes = [size_t]
    lib.ZSTD_isError.restype = ctypes.c_uint
    lib.ZSTD_getErrorName.argtypes = [size_t]
    lib.ZSTD_getErrorName.restype = ctypes.c_char_p
    lib.ZSTD_decompressStream.restype = size_t
    lib.ZSTD_decompressStream.argtypes = [ptr, ctypes.POINTER(Buffer), ctypes.POINTER(Buffer)]
    stream = lib.ZSTD_createDStream()
    check = lib.ZSTD_initDStream(stream)
    try:
        if not stream or lib.ZSTD_isError(check):
            raise AskError("Could not open the Ollama unpacker.")
        with source.open("rb") as src, output.open("wb") as dst:
            incoming = src.read(131072)
            while incoming:
                input_buffer = ctypes.create_string_buffer(incoming)
                inp = Buffer(ctypes.cast(input_buffer, ptr), len(incoming), 0)
                while inp.pos < inp.size:
                    output_buffer = ctypes.create_string_buffer(131072)
                    out = Buffer(ctypes.cast(output_buffer, ptr), len(output_buffer), 0)
                    result = lib.ZSTD_decompressStream(stream, ctypes.byref(out), ctypes.byref(inp))
                    if lib.ZSTD_isError(result):
                        detail = lib.ZSTD_getErrorName(result).decode("utf-8", "replace")
                        raise AskError("Ollama could not be unpacked: " + detail)
                    dst.write(output_buffer.raw[:out.pos])
                incoming = src.read(131072)
    finally:
        lib.ZSTD_freeDStream.argtypes = [ptr]
        lib.ZSTD_freeDStream(stream)


def _safe_members(archive):
    for member in archive.getmembers():
        target = Path(member.name)
        if target.is_absolute() or ".." in target.parts:
            raise AskError("The Ollama archive contains an unsafe path, so nothing was installed.")
        if not (member.isfile() or member.isdir() or (member.issym() and not member.linkname.startswith("/") and ".." not in Path(member.linkname).parts)):
            raise AskError("The Ollama archive contains unsupported links, so nothing was installed.")
        yield member


def install_runtime(root, progress, cancel):
    url = runtime_url()
    archive = root / "ollama.tar.zst"
    final = root / "runtime"
    staging = root / "runtime-new"
    if final.exists():
        return  # Current installation is used; no unnecessary large download.
    progress("Downloading Ollama from ollama.com: " + download_size())
    updater.download(url, archive, lambda done, total: progress(
        f"Downloading Ollama: {done // 1048576} MB / {total // 1048576} MB ({done * 100 // max(total, 1)}%)"), cancel, timeout=60)
    progress("Download complete. Unpacking Ollama locally...")
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    tar_file = root / "ollama-unpack.tar"
    _unzstd(archive, tar_file)
    archive.unlink(missing_ok=True)
    with tarfile.open(tar_file, "r:") as tar:
        try:
            tar.extractall(staging, filter="data")
        except TypeError:  # Python without extraction filters
            tar.extractall(staging, members=_safe_members(tar))
        except tarfile.FilterError as exc:
            shutil.rmtree(staging, ignore_errors=True)
            raise AskError("The Ollama archive contains an unsafe path, so nothing was installed.") from exc
    tar_file.unlink(missing_ok=True)
    exe = staging / "bin/ollama"
    if not exe.is_file():
        shutil.rmtree(staging, ignore_errors=True)
        raise AskError("The downloaded Ollama runtime is incomplete. Retry setup; the release itself was not changed.")
    exe.chmod(0o755)
    os.replace(staging, final)


def pull_model(root, progress, cancel):
    data = b'{"name":"qwen2.5:1.5b","stream":true}'
    req = Request(request_host() + "/pull", data=data, headers={"Content-Type": "application/json"}, method="POST")
    progress("Downloading the 986 MB Qwen2.5 model from Ollama...")
    total, done, digest = 0, 0, ""
    with urlopen(req, timeout=60) as response:
        if (response.headers.get("Content-Type", "")).startswith("text/html"):
            raise AskError("The model host did not speak Ollama's local protocol.")
        for raw in response:
            if cancel is not None and cancel.is_set():
                raise updater.Cancelled()
            event = json.loads(raw)
            if "error" in event:
                raise AskError("Model download failed: " + event["error"])
            if "total" in event and "digest" in event:
                if event["digest"] != digest:
                    digest = event["digest"]
                    if done != 0:
                        progress("Verifying model layer...")
                    done = 0
                total, done = event["total"], event["completed"]
                progress(f"Downloading Qwen2.5: {done // 1048576} / {total // 1048576} MB ({done * 100 // max(total, 1)}%)")
    if not model_installed():
        raise AskError("The model did not finish installing. Retry; Ollama can resume its downloaded layers.")


def ask_setup(progress=lambda text: None, cancel=None, root=None):
    root = Path(root or install_dir())
    ok, message = preflight(root)
    if not ok:
        raise AskError(message)
    root.mkdir(parents=True, exist_ok=True)
    if cancel is not None and cancel.is_set():
        raise updater.Cancelled()
    if not executable(root):
        install_runtime(root, progress, cancel)
    progress("Starting local helper only on this PC...")
    proc = start_server(root, cancel)
    try:
        if model_installed():
            return
        pull_model(root, progress, cancel)
    finally:
        stop(proc)


def ask(question, history, grounding, root=None):
    """Synchronous local inference: worker thread owns network calls; UI never blocks."""
    if not question.strip():
        raise AskError("Type a question first.")
    if len(question) > 4000:
        raise AskError("Please shorten your question to 4,000 characters or fewer.")
    proc = start_server(root)
    try:
        system = grounding() if callable(grounding) else str(grounding)
        payload = {"model": MODEL, "stream": False,
                   "options": {"temperature": .1, "num_predict": 400},
                   "messages": [{"role": "system", "content": system}]
                                 + [{"role": str(item["role"]), "content": str(item["content"])[:4000]} for item in list(history)[-8:]]
                                 + [{"role": "user", "content": question}]}
        req = Request(request_host() + "/chat", data=json.dumps(payload).encode(),
                      headers={"Content-Type": "application/json", "Accept": "application/json"}, method="POST")
        with urlopen(req, timeout=90) as response:
            data = json.load(response)
        text = data.get("message", {}).get("content", "").strip()
        if not text:
            raise AskError("The local model returned no answer. Please try again or use CodeAque Help.")
        return text
    except AskError:
        raise
    except HTTPError as exc:
        try:
            detail = json.loads(exc.read()).get("error", "")
        except Exception:
            detail = ""
        if "model" in detail.lower():
            raise AskError("The local model is missing. Choose SET UP LOCAL AI; your question was not sent elsewhere.") from exc
        raise AskError("The local helper couldn't answer. Retry setup or use CodeAque Help.") from exc
    except (OSError, TimeoutError, ValueError) as exc:
        raise AskError("The local helper couldn't answer. Check that setup completed, or retry. No online AI service was used.") from exc
    finally:
        stop(proc)
