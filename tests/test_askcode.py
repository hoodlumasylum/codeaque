"""Ask CodeAque (PC local AI helper): local-only API, setup guards, grounding prompt and UI wiring. The local Ollama API is mocked."""
import ctypes
import ctypes.util
import io
import json
import tarfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from atarilauncher import askcode


class _Mock(BaseHTTPRequestHandler):
    models = [{"name": "qwen2.5:1.5b"}]
    chat_status = 200
    seen: list = []

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/tags":
            self._send(200, {"models": type(self).models})
        else:
            self._send(404, {"error": "no"})

    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        type(self).seen.append((self.path, data))
        if self.path == "/api/chat":
            if type(self).chat_status != 200:
                self._send(type(self).chat_status, {"error": "model 'qwen2.5:1.5b' not found"})
            else:
                self._send(200, {"message": {"role": "assistant", "content": "Open Settings > Update CodeAque."}})


@pytest.fixture
def server(monkeypatch):
    _Mock.models, _Mock.chat_status, _Mock.seen = [{"name": "qwen2.5:1.5b"}], 200, []
    srv = HTTPServer(("127.0.0.1", 0), _Mock)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    monkeypatch.setenv("ATARI_ASK_HOST", f"127.0.0.1:{srv.server_port}")
    yield _Mock
    srv.shutdown()


def _no_spawn(monkeypatch):
    def boom(*a, **k):
        raise AssertionError("must not start a server")
    monkeypatch.setattr(askcode.subprocess, "Popen", boom)


def test_not_installed_gives_setup_message_and_never_spawns(monkeypatch, tmp_path):
    monkeypatch.setenv("ATARI_ASK_HOST", "127.0.0.1:9")          # nothing listens there
    monkeypatch.setattr(askcode, "executable", lambda root=None: "")
    _no_spawn(monkeypatch)
    assert "Not set up" in askcode.status()
    with pytest.raises(askcode.AskError, match="not set up"):
        askcode.ask("How do I update?", [], "prompt", root=tmp_path)


def test_answer_comes_from_local_chat_with_chosen_model_and_prompt(server, monkeypatch):
    _no_spawn(monkeypatch)                                       # server already running: reuse it, never kill it
    out = askcode.ask("How do I update?", [{"role": "user", "content": "hi"}], "SYSTEM-GROUNDING")
    assert out == "Open Settings > Update CodeAque."
    path, body = server.seen[-1]
    assert path == "/api/chat" and body["model"] == "qwen2.5:1.5b" and body["stream"] is False
    assert body["messages"][0] == {"role": "system", "content": "SYSTEM-GROUNDING"}
    assert body["messages"][-1]["content"] == "How do I update?"
    assert "key" not in json.dumps(body).lower().replace("keyboard", "")


def test_missing_model_says_to_run_setup(server, monkeypatch):
    _no_spawn(monkeypatch)
    server.chat_status = 404
    with pytest.raises(askcode.AskError, match="model is missing"):
        askcode.ask("q", [], "p")
    server.models = []
    monkeypatch.setattr(askcode, "executable", lambda root=None: "/x/ollama")
    assert "missing" in askcode.status()


@pytest.mark.parametrize("host", ["example.com:11434", "http://10.0.0.5:11434", "https://127.0.0.1:11434", "http://u:p@127.0.0.1:1"])
def test_only_loopback_is_allowed(monkeypatch, host):
    monkeypatch.setenv("ATARI_ASK_HOST", host)
    with pytest.raises(askcode.AskError):
        askcode.request_host()


def test_low_ram_and_low_disk_block_before_any_download(monkeypatch, tmp_path):
    monkeypatch.setattr(askcode.updater, "download", lambda *a, **k: pytest.fail("downloaded"))
    monkeypatch.setattr(askcode, "memory_bytes", lambda: 4 * 1024**3)
    with pytest.raises(askcode.AskError, match="8 GB"):
        askcode.ask_setup(root=tmp_path)
    monkeypatch.setattr(askcode, "memory_bytes", lambda: 16 * 1024**3)

    class U:
        free = 2 * 1024**3
    monkeypatch.setattr(askcode.shutil, "disk_usage", lambda p: U)
    with pytest.raises(askcode.AskError, match="7 GB"):
        askcode.ask_setup(root=tmp_path)


def test_official_download_urls_only():
    assert askcode.runtime_url("x86_64") == "https://ollama.com/download/ollama-linux-amd64.tar.zst"
    assert askcode.runtime_url("aarch64") == "https://ollama.com/download/ollama-linux-arm64.tar.zst"
    assert askcode.runtime_url("riscv64") == ""
    assert "2.5 GB" in askcode.download_size("x86_64")


@pytest.mark.skipif(not ctypes.util.find_library("zstd"), reason="no libzstd")
def test_unzstd_and_install_runtime(monkeypatch, tmp_path):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        data = b"#!/bin/sh\necho ok\n"
        info = tarfile.TarInfo("bin/ollama"); info.size = len(data); info.mode = 0o755
        tar.addfile(info, io.BytesIO(data))
    raw = buf.getvalue()
    lib = ctypes.CDLL(ctypes.util.find_library("zstd"))
    lib.ZSTD_compressBound.restype = ctypes.c_size_t
    lib.ZSTD_compress.restype = ctypes.c_size_t
    bound = lib.ZSTD_compressBound(ctypes.c_size_t(len(raw)))
    out = ctypes.create_string_buffer(bound)
    n = lib.ZSTD_compress(out, ctypes.c_size_t(bound), raw, ctypes.c_size_t(len(raw)), 3)

    def fake_download(url, dest, progress=None, cancel=None, timeout=0):
        assert url.startswith("https://ollama.com/download/")
        dest.write_bytes(out.raw[:n])
    monkeypatch.setattr(askcode.updater, "download", fake_download)
    askcode.install_runtime(tmp_path, lambda t: None, None)
    assert (tmp_path / "runtime/bin/ollama").read_bytes() == raw[512:512 + len(b"#!/bin/sh\necho ok\n")]
    assert askcode.executable(tmp_path)


# ------------------------------------------------------------- backend + UI
from tests.test_ui_keys import Ui, pump  # noqa: E402
from PySide6.QtCore import Qt  # noqa: E402


@pytest.fixture
def app(_qt_app):
    return _qt_app


def test_grounding_prompt_has_help_facts_and_guardrails(app, tmp_path, monkeypatch):
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        p = ui.backend._ask_grounding()
        low = p.lower()
        for must in ("i don't know", "never ask", "password", "piracy", "rom", "update codeaque", "world games", "advice"):
            assert must in low, must
        assert "projector mode is not in this pc" in low
        assert "Japanese Games" in p                              # real World Games tiles
    finally:
        ui.close()


def test_classic_settings_row_and_console_tile_open_ask(app, tmp_path, monkeypatch):
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        rows = ui.backend.setupRows()
        assert any(r["kind"] == "askcode" for r in rows)
        ui.win.setProperty("mode", "library")
        from PySide6.QtCore import QMetaObject, Q_ARG
        QMetaObject.invokeMethod(ui.win, "openAsk")
        pump(app, 100)
        assert ui.mode == "ask"
        ui.key(Qt.Key_T, 100)
        assert ui.mode == "search" and ui.win.property("oskPurpose") == "askquestion"
        ui.key(Qt.Key_Escape, 100)
        ui.win.setProperty("mode", "library")
        from atarilauncher import handheld
        assert "ask" in [k for k, _, _ in handheld.SETTINGS_TILES]
        hs = ui.win.findChild(__import__("PySide6.QtQuick", fromlist=["QQuickItem"]).QQuickItem, "handheldScreen")
        ui.backend.setHandheld(True); pump(app, 150)
        hs.menuAction.emit("ask"); pump(app, 100)
        assert ui.mode == "ask"
        assert not ui.warnings, ui.warnings
    finally:
        ui.close()


def test_submit_answer_and_error_clear_busy_and_never_persist(app, tmp_path, monkeypatch):
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        monkeypatch.setattr(askcode, "ask", lambda q, h, p, root=None: "Local answer")
        ui.backend.submitAsk("How do I add games?")
        for _ in range(50):
            pump(app, 20)
            if not ui.backend.askWorking:
                break
        assert not ui.backend.askWorking
        assert [m["content"] for m in ui.backend.askHistory] == ["How do I add games?", "Local answer"]

        def fail(*a, **k):
            raise askcode.AskError("Ask CodeAque is not set up.")
        monkeypatch.setattr(askcode, "ask", fail)
        ui.backend.submitAsk("again")
        for _ in range(50):
            pump(app, 20)
            if not ui.backend.askWorking:
                break
        assert not ui.backend.askWorking and "not set up" in ui.backend.askStatus
        ui.backend.store.save() if hasattr(ui.backend.store, "save") else None
        for f in tmp_path.rglob("*"):
            if f.is_file():
                assert b"How do I add games" not in f.read_bytes(), f
    finally:
        ui.close()
