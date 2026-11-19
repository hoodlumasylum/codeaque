#!/usr/bin/env bash
# Backup or restore CodeAque settings and game saves without opening the app.
# Games, ROMs, BIOS, firmware, Steam libraries, passwords and keys are never copied.
#
#   bash scripts/codeaque-backup.sh backup
#   bash scripts/codeaque-backup.sh backup --dest /media/code/USBSTICK
#   bash scripts/codeaque-backup.sh list
#   bash scripts/codeaque-backup.sh restore /media/code/USBSTICK/CodeAque-Backup-2026-11-19.tar.gz
set -euo pipefail

HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
APP_DIR="${ATARI_INSTALL_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/atari-launcher}"

if [ -x "$APP_DIR/venv/bin/python" ]; then
    PY="$APP_DIR/venv/bin/python"
    export PYTHONPATH="$APP_DIR${PYTHONPATH:+:$PYTHONPATH}"
elif [ -f "$REPO/atarilauncher/backup.py" ]; then
    PY="${PYTHON:-python3}"
    export PYTHONPATH="$REPO${PYTHONPATH:+:$PYTHONPATH}"
else
    PY="${PYTHON:-python3}"
fi

if ! "$PY" -c "import atarilauncher.backup" 2>/dev/null; then
    echo "CodeAque was not found. Install it first, or run this script from the CodeAque folder." >&2
    exit 1
fi

exec "$PY" -m atarilauncher.backup "$@"
