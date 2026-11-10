"""Win600 set-up: Handheld mode on, 1280x720 fullscreen + built-in pad defaults, and landscape rotation.

Only sets keys; anything else in the emulator's own settings is left alone. Safe to run again.
Rotation uses atarilauncher.rotate (same as the in-app Settings > Rotate screen) and is a no-op on
normal landscape screens.
"""
from __future__ import annotations

from pathlib import Path

from . import config, rotate

HOME = Path.home()
VAR = HOME / ".var/app"

# file -> {section or None: {key: value}}
SETTINGS: dict[Path, dict] = {
    VAR / "org.libretro.RetroArch/config/retroarch/retroarch.cfg": {None: {
        "video_fullscreen": '"true"', "video_windowed_fullscreen": '"true"',
        "video_fullscreen_x": '"1280"', "video_fullscreen_y": '"720"', "video_vsync": '"true"',
        "input_autodetect_enable": '"true"', "input_joypad_driver": '"sdl2"',
        "input_menu_toggle_gamepad_combo": '"2"',          # L3 + R3 opens the RetroArch menu
        "input_quit_gamepad_combo": '"4"',                 # hold Start + Select to quit back to CodeAque
        "menu_driver": '"ozone"', "pause_nonactive": '"false"', "savestate_auto_save": '"true"',
        "savestate_auto_load": '"true"', "video_scale_integer": '"false"',
        "video_shader_enable": '"false"', "rewind_enable": '"false"', "run_ahead_enabled": '"false"',
    }},
    VAR / "org.ppsspp.PPSSPP/config/ppsspp/PSP/SYSTEM/ppsspp.ini": {
        "Graphics": {"FullScreen": "True", "InternalResolution": "2", "FrameSkip": "0"},
    },
    VAR / "org.DolphinEmu.dolphin-emu/config/dolphin-emu/Dolphin.ini": {
        "Display": {"Fullscreen": "True", "RenderToMain": "True"},
        "Interface": {"ConfirmStop": "False"},
    },
    VAR / "org.DolphinEmu.dolphin-emu/config/dolphin-emu/GFX.ini": {
        "Settings": {"InternalResolution": "1"},
    },
    VAR / "net.pcsx2.PCSX2/config/PCSX2/inis/PCSX2.ini": {
        "UI": {"StartFullscreen": "true", "ConfirmShutdown": "false"},
        "EmuCore/GS": {"upscale_multiplier": "1"},
        "InputSources": {"SDL": "true"},
        "Pad1": {"Type": "DualShock2"},
    },
    HOME / ".local/share/duckstation/settings.ini": {
        "Main": {"StartFullscreen": "true", "ConfirmPowerOff": "false"},
        "GPU": {"ResolutionScale": "2"},
        "InputSources": {"SDL": "true"},
    },
    VAR / "org.flycast.Flycast/config/flycast/emu.cfg": {
        "window": {"fullscreen": "yes"},
        "config": {"rend.Resolution": "720"},
    },
    VAR / "net.kuribo64.melonDS/config/melonDS/melonDS.toml": {},   # melonDS: defaults are fine
}


def set_keys(path: Path, sections: dict) -> None:
    """Set key = value lines in an ini/cfg file, creating it if needed."""
    if not sections:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = path.read_text(errors="replace").splitlines() if path.exists() else []
    for section, keys in sections.items():
        sep = " = " if section is None else "="
        for key, value in keys.items():
            start, end = 0, len(lines)
            if section is not None:
                head = f"[{section}]"
                if head not in [l.strip() for l in lines]:
                    lines += ["", head]
                start = [l.strip() for l in lines].index(head) + 1
                end = next((i for i in range(start, len(lines)) if lines[i].strip().startswith("[")), len(lines))
            for i in range(start, end):
                if lines[i].split("=", 1)[0].strip() == key:
                    lines[i] = f"{key}{sep}{value}"
                    break
            else:
                lines.insert(end, f"{key}{sep}{value}")
    path.write_text("\n".join(lines) + "\n")


def turn_on_handheld(cfg_path: Path | None = None) -> None:
    cfg = config.load_config(cfg_path)
    cfg.handheld = True
    cfg.reduce_effects = "auto"
    config.save_config(cfg, cfg_path)



def reapply_boxset_boot(cfg=None, home=None, run=None) -> str:
    """If the reboot lock is on, refresh the login autostart (also used by --emulators-only)."""
    from . import boxsetboot
    cfg = cfg if cfg is not None else config.load_config()
    if getattr(cfg, "boxset_boot", False):
        return boxsetboot.persist(True, home=home, run=run)
    return ""


MARKER = Path.home() / ".config/atari/win600-setup"   # install.sh refreshes the emulator settings on every update when this exists


def main(argv: list[str] | None = None) -> int:
    import sys
    argv = sys.argv[1:] if argv is None else argv
    if "--rotate-only" in argv:
        print(rotate.apply("auto"))
        return 0
    if "--emulators-only" not in argv:
        turn_on_handheld()
        print("Handheld mode: on")
        MARKER.parent.mkdir(parents=True, exist_ok=True)
        MARKER.write_text("CodeAque Win600 set-up: install.sh / updates refresh the emulator settings\n")
    for path, sections in SETTINGS.items():
        try:
            set_keys(path, sections)
            if sections:
                print("Set up:", path.name)
        except OSError as e:
            print("Skipped", path, "-", e)
    # 0.10.31: landscape for the portrait panel (also on --emulators-only so in-app updates get it)
    try:
        print(rotate.apply("auto"))
    except Exception as exc:
        print("Rotation skipped:", exc)
    # 0.10.32: keep "Lock into Box Set Mode on reboot" autostart (also on --emulators-only)
    try:
        msg = reapply_boxset_boot()
        if msg:
            print(msg)
    except Exception as exc:
        print("Box Set autostart skipped:", exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
