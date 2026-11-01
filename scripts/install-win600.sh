#!/usr/bin/env bash
# CodeAque for the Anbernic Win600 (Linux Mint / Ubuntu). One command:
#   bash scripts/install-win600.sh
# Installs CodeAque, every emulator CodeAque supports (Flathub / official releases), sets them up for
# the 1280x720 screen and the built-in pad, turns on Handheld mode and starts CodeAque at login.
# Safe to run again. No BIOS, firmware, keys, ROMs or games are included - bring your own.
# Options:  --no-emulators   skip the emulator downloads     --no-autostart   do not start at login
set -u
APP_DIR="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
if [ ! -f "$APP_DIR/install.sh" ]; then          # run on its own (downloaded from the release): fetch CodeAque first
  command -v git >/dev/null || sudo apt-get install -y -q git
  [ -d "$HOME/codeaque/.git" ] || git clone https://github.com/hoodlumasylum/codeaque.git "$HOME/codeaque" || exit 1
  exec bash "$HOME/codeaque/scripts/install-win600.sh" "$@"
fi
EMU=1; AUTO=1
for a in "$@"; do case "$a" in --no-emulators) EMU=0;; --no-autostart) AUTO=0;; -h|--help) sed -n 2,8p "$0"; exit 0;; esac; done
say(){ printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
say "Step 1/5: basic packages (asks for your password once)"
sudo apt-get update -q || true
sudo apt-get install -y -q python3 python3-venv python3-pip git curl flatpak unzip p7zip-full ffmpeg || echo "Some packages failed - carrying on."
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo || true

say "Step 2/5: CodeAque itself"
if [ "$AUTO" = 1 ]; then bash "$APP_DIR/install.sh" --autostart; else bash "$APP_DIR/install.sh"; fi

if [ "$EMU" = 1 ]; then
  say "Step 3/5: emulators (this is the long part - 20 to 60 minutes on Wi-Fi)"
  bash "$APP_DIR/scripts/install-everything.sh" --yes \
    retroarch duckstation pcsx2 ppsspp dolphin cemu azahar melonds desmume mgba flycast rmg mupen64plus \
    mednafen mame dosbox scummvm fsuae vice xemu rpcs3 ryujinx \
    chrome kodi moonlight antimicrox ffmpeg 7zip unrar playerctl || echo "Some emulators failed - run this script again later."
else say "Step 3/5: skipped (--no-emulators)"; fi

say "Step 4/5: settings for the 1280x720 screen and the built-in pad"
PY="$APP_DIR/.venv/bin/python"; [ -x "$PY" ] || PY=python3
PYTHONPATH="$APP_DIR" "$PY" -m atarilauncher.win600 || echo "Could not write every emulator setting - they still work with their own defaults."

say "Step 5/5: done"
cat <<T
CodeAque is installed in Handheld mode.
 - Restart (or log out and in) and CodeAque opens by itself.
 - Add your own games: Settings > Add games. BIOS files you own: Settings > Add BIOS files.
 - To leave Handheld mode: Settings > Leave Handheld.
T
