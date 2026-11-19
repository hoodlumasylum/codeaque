#!/usr/bin/env bash
# CodeAque installer: copies the app to ~/.local/share/atari-launcher, makes a private Python
# environment and adds an app-menu entry. No root needed (except optionally for a few system
# libraries, and it asks first). Safe to run again to upgrade: your settings, favourites and
# play history (~/.config/atari, ~/.local/share/atari) are never touched.
#
#   ./install.sh                 normal install / upgrade
#   ./install.sh --autostart     also start CodeAque automatically when you log in
#   ./install.sh --yes           don't ask questions; also set up Steam (steam-installer, i386) and CodeAque-only mode
#   ./install.sh --yes --autostart   the usual one-command update: libraries, Steam, CodeAque-only session, start at login
#   ./install.sh --no-gamepad    skip the controller library
#   ./install.sh --no-studio     skip numpy (only needed by the music Studio)
#   ./install.sh --no-system-packages   never use sudo / the package manager (used by CodeAque's own updater)
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
APP_DIR="${ATARI_INSTALL_DIR:-$DATA_HOME/atari-launcher}"
AUTOSTART=0; ASSUME_YES=0; GAMEPAD=1; SYSPKGS=1; STUDIO=1
for arg in "$@"; do
    case "$arg" in
        --autostart) AUTOSTART=1 ;;
        --yes|-y) ASSUME_YES=1 ;;
        --no-gamepad) GAMEPAD=0 ;;
        --no-studio) STUDIO=0 ;;
        --no-system-packages) SYSPKGS=0 ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

say()  { printf '\n\033[1;33m==>\033[0m \033[1m%s\033[0m\n' "$*"; }
warn() { printf '\033[1;31m!!\033[0m %s\n' "$*" >&2; }
ask()  { [ "$ASSUME_YES" = 1 ] && return 0; [ -t 0 ] || return 1; read -r -p "$1 [Y/n] " a; case "${a:-y}" in [Yy]*) return 0 ;; *) return 1 ;; esac; }

if [ "$(id -u)" = 0 ] && [ "${ATARI_ALLOW_ROOT_BUILD:-0}" != 1 ]; then   # (ATARI_ALLOW_ROOT_BUILD: the Live USB image build only)
    warn "Please run this as your normal user, not with sudo. (It will ask for your password only if it needs to.)"
    exit 1
fi

# ---------------------------------------------------------------- system libraries, Steam, CodeAque-only mode
say "Checking system libraries"
if [ "$SYSPKGS" = 0 ]; then
    echo "Skipping system packages (already set up)."
elif command -v apt-get >/dev/null 2>&1; then
    PKGS="python3 python3-venv python3-pip xdotool wmctrl libxcb-cursor0 libxkbcommon-x11-0 libegl1 libgl1 libfontconfig1 libdbus-1-3 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 libxcb-render-util0 libxcb-shape0 libxcb-xinerama0 libxcb-xkb1"
    MISSING=""
    for p in $PKGS; do dpkg -s "$p" >/dev/null 2>&1 || MISSING="$MISSING $p"; done
    ENABLE_SH=""
    if ENABLE_SH="$(mktemp)"; ATARI_INSTALL_DIR="$APP_DIR" CA_USER="$(id -un)" PYTHONPATH="$SRC_DIR" python3 -c "import os; from pathlib import Path; from atarilauncher import kiosk; print(kiosk.enable_script(os.environ['CA_USER'], run_sh=Path(os.environ['ATARI_INSTALL_DIR'])/'run.sh'))" > "$ENABLE_SH"; then
        echo "CodeAque-only session script is ready."
    else
        warn "Could not prepare the CodeAque-only session files (continuing). You can still use Settings > Start mode later."
        rm -f "$ENABLE_SH"
        ENABLE_SH=""
    fi
    if [ -n "$MISSING" ]; then
        echo "CodeAque needs these packages:$MISSING"
    fi
    if ask "Install Steam (steam-installer + 32-bit libraries), any missing CodeAque libraries, and set up CodeAque-only mode now? (one password)"; then
        say "Setting up Steam and CodeAque-only mode (one password; a failed apt step will not stop the rest)"
        # One sudo: i386, apt update, packages, then the session files. Each apt step is allowed to fail.
        sudo env MISSING="$MISSING" ENABLE_SH="$ENABLE_SH" SETUP_USER="$(id -un)" bash -c '
            set +e
            echo "==> Adding 32-bit (i386) support (needed by Steam)..."
            dpkg --add-architecture i386 || echo "!! Could not add the i386 architecture (continuing)."
            echo "==> Updating package lists..."
            apt-get update || echo "!! apt-get update failed (continuing)."
            if [ -n "$MISSING" ]; then
                echo "==> Installing libraries CodeAque needs:$MISSING"
                # unquoted on purpose: MISSING is a space-separated list
                apt-get install -y $MISSING || echo "!! Some CodeAque libraries could not be installed (continuing)."
            fi
            echo "==> Installing steam-installer..."
            apt-get install -y steam-installer || echo "!! steam-installer could not be installed (continuing). You can still press Fix Steam in CodeAque later."
            echo "==> Installing 32-bit Steam runtime libraries..."
            apt-get install -y libc6:i386 libgl1:i386 libgl1-mesa-dri:i386 || echo "!! Some 32-bit libraries could not be installed (continuing)."
            echo "==> Installing packages CodeAque-only mode needs (policykit, openbox)..."
            apt-get install -y policykit-1 pkexec openbox dbus xdg-utils || echo "!! Some CodeAque-only packages could not be installed (continuing)."
            echo "==> Letting the gamepad be used (input group + steam-devices)..."
            getent group input >/dev/null 2>&1 || groupadd input || echo "!! Could not create the input group (continuing)."
            if [ -n "$SETUP_USER" ]; then
                usermod -aG input "$SETUP_USER" || echo "!! Could not add $SETUP_USER to the input group (continuing). You can run: sudo usermod -aG input $SETUP_USER"
            fi
            apt-get install -y steam-devices || echo "!! steam-devices could not be installed (continuing). Fix Steam controller in CodeAque can still try later."
            mkdir -p /etc/udev/rules.d
            printf '%s\n' '# CodeAque: let the input group read gamepad event nodes' 'KERNEL=="event*", SUBSYSTEM=="input", MODE="0660", GROUP="input"' 'KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"' > /etc/udev/rules.d/99-codeaque-input.rules || echo "!! Could not write the gamepad udev rule (continuing)."
            udevadm control --reload-rules >/dev/null 2>&1 || true
            udevadm trigger >/dev/null 2>&1 || true
            echo "Gamepad access: $SETUP_USER was added to the input group (log out or restart once for it to take effect)."
            if [ -n "$ENABLE_SH" ] && [ -f "$ENABLE_SH" ]; then
                echo "==> Writing CodeAque-only session files (takes effect after you log out or restart)..."
                sh "$ENABLE_SH" || echo "!! Could not write the CodeAque-only session files (continuing). In a Terminal you can run the repair line from Settings > Start mode."
            fi
            echo "==> System setup step finished."
            exit 0
        ' || warn "The system setup step did not finish cleanly; CodeAque itself will still be installed."
        rm -f "$ENABLE_SH"
    else
        [ -n "$MISSING" ] && warn "Skipping packages; CodeAque may fail to start until they are installed."
        rm -f "$ENABLE_SH"
    fi
elif command -v dnf >/dev/null 2>&1; then
    PKGS="python3 python3-pip xdotool wmctrl xcb-util-cursor xcb-util-image xcb-util-keysyms xcb-util-renderutil xcb-util-wm libxkbcommon-x11 mesa-libEGL mesa-libGL fontconfig dbus-libs"
    MISSING=""
    for p in $PKGS; do rpm -q "$p" >/dev/null 2>&1 || MISSING="$MISSING $p"; done
    if [ -n "$MISSING" ]; then
        echo "CodeAque needs these packages:$MISSING"
        if ask "Install them now with sudo dnf?"; then
            sudo dnf install -y $MISSING || warn "Some packages could not be installed (on Bazzite/Silverblue-style systems this is normal); continuing."
        fi
    else
        echo "All needed packages are already installed."
    fi
else
    echo "Unknown package manager. If CodeAque fails to start, install Python 3 with venv/pip, plus the Qt/xcb libraries (see README)."
fi

command -v python3 >/dev/null 2>&1 || { warn "python3 was not found. Install Python 3 and run this again."; exit 1; }
python3 - <<'PY' || { warn "Python 3.9 or newer is required."; exit 1; }
import sys
sys.exit(0 if sys.version_info >= (3, 9) else 1)
PY

# ---------------------------------------------------------------- copy the app
VERSION="$(sed -n 's/^__version__ = "\(.*\)"/\1/p' "$SRC_DIR/atarilauncher/__init__.py")"
OLD_VERSION=""
[ -f "$APP_DIR/atarilauncher/__init__.py" ] && OLD_VERSION="$(sed -n 's/^__version__ = "\(.*\)"/\1/p' "$APP_DIR/atarilauncher/__init__.py")"
if [ "$SRC_DIR" != "$APP_DIR" ]; then
    if [ -n "$OLD_VERSION" ]; then say "Upgrading CodeAque $OLD_VERSION -> ${VERSION:-new} (your settings and favourites are kept)"
    else say "Installing CodeAque ${VERSION:-} into $APP_DIR"; fi
    mkdir -p "$APP_DIR"
    rm -rf "$APP_DIR/atarilauncher" "$APP_DIR/scripts" "$APP_DIR/packaging"
    for item in atarilauncher scripts packaging run.sh install.sh uninstall.sh requirements.txt requirements-gamepad.txt requirements-studio.txt pyproject.toml README.md LICENSE; do
        [ -e "$SRC_DIR/$item" ] && cp -a "$SRC_DIR/$item" "$APP_DIR/"
    done
    find "$APP_DIR" -name __pycache__ -type d -prune -exec rm -rf {} + 2>/dev/null || true
fi
chmod +x "$APP_DIR/run.sh" "$APP_DIR/install.sh" "$APP_DIR/uninstall.sh" "$APP_DIR"/scripts/*.sh 2>/dev/null || true

# ---------------------------------------------------------------- virtual environment
say "Creating a private Python environment"
if [ -x "$APP_DIR/.venv/bin/python" ] && "$APP_DIR/.venv/bin/python" -c "import sys" >/dev/null 2>&1; then
    echo "Re-using the existing environment."
elif ! python3 -m venv --clear "$APP_DIR/.venv"; then
    warn "Could not create the environment. On Ubuntu/Mint run:  sudo apt install python3-venv   then run ./install.sh again."
    exit 1
fi
"$APP_DIR/.venv/bin/python" -m pip install --quiet --upgrade pip

say "Installing CodeAque's Python libraries (this downloads about 150 MB the first time)"
"$APP_DIR/.venv/bin/python" -m pip install -r "$APP_DIR/requirements.txt"
if [ "$GAMEPAD" = 1 ]; then
    say "Installing controller support"
    "$APP_DIR/.venv/bin/python" -m pip install -r "$APP_DIR/requirements-gamepad.txt" \
        || warn "Controller support could not be installed. CodeAque still works with the keyboard."
fi

if [ "$STUDIO" = 1 ]; then
    say "Installing music Studio support (numpy, about 20 MB)"
    "$APP_DIR/.venv/bin/python" -m pip install -r "$APP_DIR/requirements-studio.txt" \
        || { echo "Trying once more..."; sleep 3; "$APP_DIR/.venv/bin/python" -m pip install -r "$APP_DIR/requirements-studio.txt"; } \
        || warn "Studio support could not be installed. Everything else works; in CodeAque press Studio > Fix Studio (or Help > Fix Studio) to add it."
    if "$APP_DIR/.venv/bin/python" -c "import numpy" >/dev/null 2>&1; then echo "Studio support: OK"; else warn "Studio support is NOT installed yet (CodeAque's Studio screen has a Fix Studio button)."; fi
fi

say "Testing that the app can start"
if ! QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=software ATARI_NO_GAMEPAD=1 ATARI_NO_COVERS=1 ATARI_NO_UPDATE_CHECK=1 PYTHONPATH="$APP_DIR" \
        "$APP_DIR/.venv/bin/python" -m atarilauncher --demo --windowed --selftest >/dev/null; then
    warn "The start-up test failed - see the messages above. (Missing system libraries are the usual cause; see the README.)"
    exit 1
fi
# the first-start screen must be leavable with Esc (a bug in 0.2.0): start with an empty private config, press Esc, expect config.toml
ST_DIR="$(mktemp -d)"
if QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=software ATARI_NO_GAMEPAD=1 ATARI_NO_COVERS=1 ATARI_NO_UPDATE_CHECK=1 PYTHONPATH="$APP_DIR" \
        HOME="$ST_DIR" ATARI_CONFIG_DIR="$ST_DIR/conf" ATARI_DATA_DIR="$ST_DIR/data" \
        "$APP_DIR/.venv/bin/python" -m atarilauncher --windowed --selftest --screenshot-keys "wait:200,back,wait:300" >/dev/null \
        && [ -f "$ST_DIR/conf/config.toml" ]; then
    rm -rf "$ST_DIR"
else
    rm -rf "$ST_DIR"
    warn "The first-start screen test failed - see the messages above."
    exit 1
fi
echo "OK"

# ---------------------------------------------------------------- app menu entry
say "Adding CodeAque to your application menu"
chmod +x "$APP_DIR/run.sh"
BIN_DIR="$HOME/.local/bin"; APPS_DIR="$DATA_HOME/applications"
ICON_DIR="$DATA_HOME/icons/hicolor/scalable/apps"
mkdir -p "$BIN_DIR" "$APPS_DIR" "$ICON_DIR"
ln -sf "$APP_DIR/run.sh" "$BIN_DIR/codeaque"       # the CodeAque command (the product name)
ln -sf "$APP_DIR/run.sh" "$BIN_DIR/atari"          # the original command name keeps working (older shortcuts, autostart, CodeAque-only mode)
# `code` is the new command name. Visual Studio Code also installs a command called `code`, so never take that name over:
# only add ours when nothing else on this computer already answers to `code` (or when it is already our own link).
CODE_LINK="$BIN_DIR/code"; CODE_NOTE=""
if [ -L "$CODE_LINK" ] && [ "$(readlink "$CODE_LINK")" = "$APP_DIR/run.sh" ]; then
    :
elif [ -e "$CODE_LINK" ] || [ -L "$CODE_LINK" ] || PATH="$(printf '%s' "$PATH" | sed "s|$BIN_DIR:||g;s|:$BIN_DIR||g")" command -v code >/dev/null 2>&1; then
    CODE_NOTE="The name 'code' is already used on this computer (probably Visual Studio Code), so it was left alone. Use 'codeaque', 'atari' or 'code-launcher' to start CodeAque from a terminal."
else
    ln -s "$APP_DIR/run.sh" "$CODE_LINK"
fi
ln -sf "$APP_DIR/run.sh" "$BIN_DIR/code-launcher"
ln -sf "$APP_DIR/scripts/install-everything.sh" "$BIN_DIR/atari-install-everything"
ln -sf "$APP_DIR/scripts/install-emulators.sh" "$BIN_DIR/atari-install-emulators"
cp "$APP_DIR/packaging/atari-launcher.svg" "$ICON_DIR/atari-launcher.svg"
for n in 512 256 128 64; do                         # the CodeAque logo as ordinary icon sizes too (some menus ignore SVG icons)
    if [ -f "$APP_DIR/packaging/icons/code-$n.png" ]; then
        mkdir -p "$DATA_HOME/icons/hicolor/${n}x${n}/apps"
        cp "$APP_DIR/packaging/icons/code-$n.png" "$DATA_HOME/icons/hicolor/${n}x${n}/apps/atari-launcher.png"
    fi
done
sed "s|@RUN@|$APP_DIR/run.sh|g" "$APP_DIR/packaging/atari.desktop.in" > "$APPS_DIR/atari.desktop"
chmod +x "$APPS_DIR/atari.desktop"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" 2>/dev/null || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -t "$DATA_HOME/icons/hicolor" 2>/dev/null || true

if [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/autostart/atari.desktop" ] && [ "$AUTOSTART" != 1 ]; then
    cp "$APPS_DIR/atari.desktop" "${XDG_CONFIG_HOME:-$HOME/.config}/autostart/atari.desktop"      # keep an existing autostart, with the new name
fi
if [ "$AUTOSTART" = 1 ]; then
    mkdir -p "${XDG_CONFIG_HOME:-$HOME/.config}/autostart"
    cp "$APPS_DIR/atari.desktop" "${XDG_CONFIG_HOME:-$HOME/.config}/autostart/atari.desktop"
    echo "CodeAque will now start automatically when you log in."
fi

# Leftovers of older CodeAque copies: shortcuts / autostart entries that start another copy are pointed at this one, and an old
# CodeAque intro video chosen in Settings is removed so the current splash plays (your own videos stay). No password needed.
if [ -x "$APP_DIR/.venv/bin/python" ]; then
    (cd "$APP_DIR" && PYTHONPATH="$APP_DIR" "$APP_DIR/.venv/bin/python" -m atarilauncher.staleparts "$APP_DIR") || echo "Could not check for old CodeAque shortcuts."
fi

# Win600 / handheld set-ups (made by scripts/install-win600.sh): refresh the emulators' 1280x720 + built-in pad settings,
# so an in-app update brings new emulator settings too (only those keys; Handheld mode on/off is left as you chose).
if [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/atari/win600-setup" ]; then
    (cd "$APP_DIR" && PYTHONPATH="$APP_DIR" "$APP_DIR/.venv/bin/python" -m atarilauncher.win600 --emulators-only) || warn "Could not refresh the emulator settings."
fi

say "Done!"
cat <<EOT
CodeAque ${VERSION:-} is installed. Start it from your application menu (look for "CodeAque" under Games),
or in a terminal with:   code   or   atari      (add --windowed to run in a window)
${CODE_NOTE:+$CODE_NOTE
}
You can now delete the folder you extracted - the app lives in $APP_DIR.
The first start shows a setup screen; "Add Games" copies games from a USB stick.
Need emulators or apps (PlayStation, Nintendo, Xbox, Roblox, ...)?  Open Settings > Emulators > Install emulators and apps,
or run in a terminal:   atari-install-everything
To start CodeAque at login:  ./install.sh --autostart
If you used --yes, Steam (steam-installer) and CodeAque-only mode were also set up.
CodeAque-only mode takes effect after you log out or restart. To do it by hand:
  cd ~/.local/share/atari-launcher && rm -f ~/.config/atari/desktop-once && PYTHONPATH=. python3 -c "from atarilauncher import kiosk; print(kiosk.enable_script('\$USER'))" | sudo sh
EOT
case ":$PATH:" in *":$BIN_DIR:"*) ;; *) echo "Note: $BIN_DIR is not on your PATH yet; log out/in to use the 'atari' command." ;; esac
