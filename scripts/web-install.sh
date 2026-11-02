#!/usr/bin/env bash
# CodeAque one-click installer / updater (no terminal needed). Started by CodeAque-Install.desktop (double-click it).
# Gets the latest release from GitHub, checks it against the release's SHA256SUMS.txt, installs it for your own
# account and starts CodeAque. A graphical password box (pkexec) appears only if Python's venv support is missing.
# No BIOS, firmware, keys, ROMs or games are downloaded.
set -uo pipefail
REPO=hoodlumasylum/codeaque
LOG="${XDG_CACHE_HOME:-$HOME/.cache}/atari/web-install.log"; mkdir -p "$(dirname "$LOG")"; exec 3>&1 >"$LOG" 2>&1
have() { command -v "$1" >/dev/null 2>&1; }
tell() { if have zenity; then zenity --info --title=CodeAque --width=380 --text="$1" 2>/dev/null; elif have notify-send; then notify-send CodeAque "$1"; else echo "$1" >&3; fi; }
fail() { if have zenity; then zenity --error --title=CodeAque --width=420 --text="$1\n\nDetails: $LOG" 2>/dev/null; else tell "$1 (details: $LOG)"; fi; exit 1; }
step() { echo "# $1"; echo "$2"; }

run() {
  step "Looking for the latest CodeAque ..." 5
  JSON=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest") || fail "Could not reach GitHub. Check the internet connection and try again."
  read -r VER TAR SUMS < <(python3 - "$JSON" <<'P'
import json, sys
d = json.loads(sys.argv[1]); v = d["tag_name"].lstrip("vV"); a = {x["name"]: x["browser_download_url"] for x in d["assets"]}
print(v, a.get(f"atari-{v}.tar.gz", "-"), a.get("SHA256SUMS.txt", "-"))
P
  )
  [ "${TAR:--}" != "-" ] && [ "${SUMS:--}" != "-" ] || fail "The latest release has no app download."
  W=$(mktemp -d)
  step "Downloading CodeAque $VER ..." 20
  curl -fsSL -o "$W/atari-$VER.tar.gz" "$TAR" && curl -fsSL -o "$W/SHA256SUMS.txt" "$SUMS" || fail "The download failed. Please try again."
  step "Checking the download is genuine ..." 45
  (cd "$W" && grep " atari-$VER.tar.gz\$" SHA256SUMS.txt | sha256sum -c -) || fail "The download did not match its checksum, so nothing was installed."
  if have dpkg; then             # the system parts CodeAque needs (the same list as install.sh); usually already there
    MISSING=""
    for p in python3 python3-venv python3-pip xdotool wmctrl libxcb-cursor0 libxkbcommon-x11-0 libegl1 libgl1 libfontconfig1 \
             libdbus-1-3 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 libxcb-render-util0 libxcb-shape0 libxcb-xinerama0 libxcb-xkb1; do
      dpkg -s "$p" >/dev/null 2>&1 || MISSING="$MISSING $p"
    done
    if [ -n "$MISSING" ]; then
      step "Adding a few system parts (a password box may appear) ..." 55
      have pkexec && pkexec apt-get install -y $MISSING || fail "Could not add:$MISSING. Install them with the Software Manager, then run this again."
    fi
  fi
  step "Installing CodeAque $VER (a few minutes) ..." 65
  tar -xzf "$W/atari-$VER.tar.gz" -C "$W" || fail "Could not unpack the download."
  (cd "$W/atari" && bash install.sh --yes --no-system-packages) || fail "The installer stopped."
  rm -rf "$W"
  step "Done!" 100
}

if have zenity; then
  run | zenity --progress --title="CodeAque installer" --width=420 --auto-close --no-cancel 2>/dev/null
  ok=${PIPESTATUS[0]}
else
  run; ok=$?
fi
[ "$ok" = 0 ] || exit 1
tell "CodeAque is installed. It is in your menu under Games, and opens now."
(nohup "$HOME/.local/bin/atari" >/dev/null 2>&1 &) || true
