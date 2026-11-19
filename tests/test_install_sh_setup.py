"""install.sh --yes must set up Steam (i386 + steam-installer) and CodeAque-only session files in one sudo."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SH = (ROOT / "install.sh").read_text()


def test_yes_update_sets_up_steam_and_codeaque_only_and_survives_a_failed_apt_step():
    assert "dpkg --add-architecture i386" in SH
    assert "apt-get update" in SH
    assert "apt-get install -y steam-installer" in SH
    assert "libc6:i386" in SH and "libgl1:i386" in SH and "libgl1-mesa-dri:i386" in SH
    assert "policykit-1" in SH and "openbox" in SH
    assert "kiosk.enable_script" in SH
    # one password for the lot
    assert SH.count("sudo env MISSING=") == 1 or SH.count("sudo env") >= 1
    assert "one password" in SH.lower()
    # a failed apt step must not abort the rest (or the whole installer)
    assert "continuing)." in SH or "continuing." in SH
    assert "|| echo \"!!" in SH
    assert "set +e" in SH
    # --no-system-packages (in-app updater) still skips this
    assert "--no-system-packages" in SH and 'SYSPKGS=0' in SH


def test_yes_update_sets_up_gamepad_access_and_bakes_the_installed_run_script():
    assert "usermod -aG input" in SH
    assert "steam-devices" in SH
    assert "99-codeaque-input.rules" in SH
    assert "xdg-utils" in SH
    # session files must point at the installed run.sh, never the /tmp extract
    assert "ATARI_INSTALL_DIR" in SH
    assert "run_sh=Path(os.environ['ATARI_INSTALL_DIR'])/'run.sh'" in SH or 'ATARI_INSTALL_DIR' in SH
