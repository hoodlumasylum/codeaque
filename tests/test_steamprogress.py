"""Steam install / bootstrap progress: parse logs and show a wait note if the runtime dialog appears mid-download."""
from pathlib import Path

from atarilauncher import steamprogress as sp


def test_parses_bootstrap_download_line_with_percent_and_kb():
    p = sp.parse_line("[19:20:26] [  2%] Downloading update (13,816 of 502,699 KB)...")
    assert p is not None and p.still_downloading
    assert p.fraction == 0.02 and p.percent_text == "2%"
    assert "13,816" in p.detail and "502,699" in p.detail
    assert p.text == "Steam is downloading its update... 2%"


def test_parses_stdout_download_without_bracket_percent():
    p = sp.parse_line("[----] Downloading update (1,234 of 56,789 KB)...")
    assert p is not None and p.still_downloading
    assert p.percent_text == "2%"   # 1234/56789 ≈ 2%
    assert p.text.startswith("Steam is downloading its update...")


def test_parses_100_percent_and_stage_lines():
    assert sp.parse_line("[100%] Downloading update (51208 of 51208 KB)...").percent_text == "100%"
    assert sp.parse_line("Verifying installation...").stage == "verify"
    assert sp.parse_line("Extracting package...").stage == "extract"
    assert sp.parse_line("Installing update...").stage == "install"
    assert sp.parse_line("Download complete.").stage == "downloaded"
    done = sp.parse_line("Update complete, launching Steam...")
    assert done.ready and done.fraction == 1.0 and done.percent_text == "100%"
    assert sp.parse_line("Verification complete").ready
    assert sp.parse_line("Nothing to do").ready
    assert sp.parse_line("Checking for available updates...").stage == "check"


def test_parses_apt_progress_and_package_lines():
    p = sp.parse_line("Progress: [ 45%]")
    assert p.stage == "apt" and p.percent_text == "45%" and p.text == "Installing Steam packages... 45%"
    assert sp.parse_line("Unpacking steam-installer (1:1.0.0.79) ...").stage == "apt"
    assert "Installing Steam packages" in sp.parse_line("Setting up libc6:i386 (2.39-0ubuntu8) ...").text
    assert sp.parse_line("45% complete").percent_text == "45%"


def test_runtime_dialog_during_download_tells_the_user_to_wait():
    text = """
[19:20:26] [ 45%] Downloading update (200,000 of 444,000 KB)...
Couldn't set up the Steam Runtime. Are you running low on disk space?
"""
    p = sp.parse_text(text)
    assert p is not None and p.wait_for_download and p.still_downloading
    assert p.percent_text == "45%"
    assert "please wait" in p.text.lower()
    row = p.row()
    assert row["kind"] == "progress" and row["value"] == "45%" and row["fraction"] == 0.45
    assert "still downloading" in row["detail"] and "disk" in row["detail"].lower()
    assert row["big"] is True


def test_runtime_error_alone_is_flagged_but_not_a_wait():
    p = sp.parse_line("Couldn't set up the Steam Runtime. Are you running low on disk space?")
    assert p is not None and p.runtime_error and not p.wait_for_download and not p.still_downloading


def test_latest_line_wins_and_ignores_unrelated_text():
    text = """
random noise
[ 10%] Downloading update (10 of 100 KB)...
Installing update...
"""
    p = sp.parse_text(text)
    assert p.stage == "install" and not p.still_downloading
    assert sp.parse_line("hello world") is None
    assert sp.parse_text("") is None


def test_watcher_only_reads_bytes_appended_after_start(tmp_path, monkeypatch):
    home = tmp_path / "home"
    log = home / ".local/share/Steam/logs/bootstrap_log.txt"
    log.parent.mkdir(parents=True)
    log.write_text("[12:00:00] [ 99%] Downloading update (99 of 100 KB)...\n")
    w = sp.Watcher(home)
    # Old 99% must not appear — it was already in the file when we started.
    assert w.poll().stage == "start" and w.poll().fraction is None
    log.write_text(log.read_text() + "[12:01:00] [ 45%] Downloading update (45 of 100 KB)...\n")
    p = w.poll()
    assert p.percent_text == "45%" and p.still_downloading
    log.write_text(log.read_text() + "Couldn't set up the Steam Runtime. Are you running low on disk space?\n")
    p = w.poll()
    assert p.wait_for_download and p.percent_text == "45%"
    log.write_text(log.read_text() + "Update complete, launching Steam...\n")
    p = w.poll()
    assert p.ready and p.percent_text == "100%"
    # A later ui_ready with no download in flight marks ready even without a new line.
    w2 = sp.Watcher(home)
    assert w2.poll(ui_ready=True).ready


def test_watcher_handles_truncated_log_and_ui_ready_during_download(tmp_path):
    home = tmp_path / "home"
    log = home / ".steam/steam/logs/bootstrap_log.txt"
    log.parent.mkdir(parents=True)
    log.write_text("start\n")
    w = sp.Watcher(home)
    log.write_text("[ 20%] Downloading update (20 of 100 KB)...\n")  # truncated then rewritten
    p = w.poll()
    assert p.percent_text == "20%"
    # A Steam window can appear while the update is still running: keep the bar.
    p = w.poll(ui_ready=True)
    assert p.still_downloading and p.percent_text == "20%" and not p.ready


def test_from_logs_reads_bootstrap_file(tmp_path):
    home = tmp_path / "home"
    log = home / ".local/share/Steam/logs/bootstrap_log.txt"
    log.parent.mkdir(parents=True)
    log.write_text("[  7%] Downloading update (7,000 of 100,000 KB)...\n")
    p = sp.from_logs(home)
    assert p.percent_text == "7%" and "Steam is downloading" in p.text


def test_row_is_indeterminate_when_percent_is_unknown():
    p = sp.parse_line("Verifying installation...")
    row = p.row()
    assert row["fraction"] == -1.0 and row["value"] == "" and "verifying" in row["title"].lower()
    start = sp.starting().row()
    assert start["fraction"] == -1.0 and "starting" in start["title"].lower()
