#!/usr/bin/env bash
# CodeAque-branded GRUB start-up menu (CodeAque, Windows, Mac) plus an optional Plymouth splash.
#
# Install (from a Terminal on this computer):
#   curl -fsSL https://raw.githubusercontent.com/hoodlumasylum/codeaque/main/scripts/install-bootmenu.sh | sudo bash
#
# Undo:
#   curl -fsSL https://raw.githubusercontent.com/hoodlumasylum/codeaque/main/scripts/install-bootmenu.sh | sudo bash -s -- --undo
#
# This script never installs GRUB to a disk, never changes the firmware boot order,
# and never formats or writes to Windows or macOS partitions.
set -euo pipefail

REPO_RAW="https://raw.githubusercontent.com/hoodlumasylum/codeaque/main"
THEME_DIR_REL="scripts/bootmenu-theme"
PLY_SRC_REL="atarilauncher/bootscreen"

BACKUP_DIR="/var/lib/codeaque-bootmenu"
GRUB_THEME_DIR="/boot/grub/themes/codeaque"
PLY_DIR="/usr/share/plymouth/themes/codeaque"
GRUB_D_CFG="/etc/default/grub.d/zzz-codeaque-boot.cfg"
MENU_SCRIPT="/etc/grub.d/06_codeaque"
WIN_SCRIPT="/etc/grub.d/35_codeaque_windows"
MAC_SCRIPT="/etc/grub.d/36_codeaque_mac"
GRUB_DEFAULT="/etc/default/grub"
GRUB_CFG="/boot/grub/grub.cfg"
LINUX_SCRIPT="/etc/grub.d/10_linux"
OSPROBER_SCRIPT="/etc/grub.d/30_os-prober"
UEFI_SCRIPT="/etc/grub.d/30_uefi-firmware"

# Optional overrides for tests: a fake root and a local asset folder.
ROOT="${CODEAQUE_BOOTMENU_ROOT:-}"
ASSET_DIR="${CODEAQUE_BOOTMENU_ASSETS:-}"
SKIP_UPDATE="${CODEAQUE_BOOTMENU_SKIP_UPDATE:-}"

usage() {
  cat <<'EOF'
CodeAque boot menu installer

  sudo bash install-bootmenu.sh           Install or refresh the menu
  sudo bash install-bootmenu.sh --undo    Put the old boot menu back
  bash install-bootmenu.sh --help         Show this help
  bash install-bootmenu.sh --selftest     Check the script without changing this computer

One-line install:
  curl -fsSL https://raw.githubusercontent.com/hoodlumasylum/codeaque/main/scripts/install-bootmenu.sh | sudo bash
EOF
}

say() { printf '%s\n' "$*"; }
err() { printf 'Error: %s\n' "$*" >&2; }

# Prefix every system path when testing under a fake root.
p() {
  if [ -n "$ROOT" ]; then
    printf '%s%s' "$ROOT" "$1"
  else
    printf '%s' "$1"
  fi
}

need_root() {
  if [ "${EUID:-$(id -u)}" -eq 0 ]; then
    return 0
  fi
  if [ -n "$ROOT" ] || [ "${1:-}" = "--selftest" ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    return 0
  fi
  if [ -f "${BASH_SOURCE[0]:-}" ] && [ "${BASH_SOURCE[0]}" != "bash" ] && [ "${BASH_SOURCE[0]}" != "-" ] && [ "${BASH_SOURCE[0]}" != "sh" ]; then
    say "This needs your Mint password (sudo)."
    exec sudo -E env CODEAQUE_BOOTMENU_ROOT="${ROOT}" CODEAQUE_BOOTMENU_ASSETS="${ASSET_DIR}" CODEAQUE_BOOTMENU_SKIP_UPDATE="${SKIP_UPDATE}" bash "${BASH_SOURCE[0]}" "$@"
  fi
  err "Please run this with sudo, for example:"
  err "  curl -fsSL ${REPO_RAW}/scripts/install-bootmenu.sh | sudo bash"
  exit 1
}

have() { command -v "$1" >/dev/null 2>&1; }

# ---------------------------------------------------------------------------
# Generated GRUB / theme / Plymouth text
# ---------------------------------------------------------------------------

theme_txt() {
  cat <<'EOF'
# CodeAque boot menu (scripts/install-bootmenu.sh)
title-text: ""
desktop-image: "background.png"
desktop-image-scale-method: "crop"
desktop-color: "#050507"
terminal-font: "CodeAque Regular 20"
terminal-box: "terminal_box_*.png"

+ boot_menu {
    left = 27%
    top = 63%
    width = 46%
    height = 27%
    item_font = "CodeAque Regular 36"
    selected_item_font = "CodeAque Regular 36"
    item_color = "#e8eaf2"
    selected_item_color = "#ffffff"
    item_height = 52
    item_padding = 18
    item_spacing = 10
    icon_width = 0
    icon_height = 0
    selected_item_pixmap_style = "select_*.png"
    scrollbar = false
}

+ label {
    id = "__timeout__"
    left = 10%
    top = 93%
    width = 80%
    align = "center"
    color = "#ff8a8a"
    font = "CodeAque Regular 24"
    text = "Starting CodeAque in %ds"
}
EOF
}

grub_defaults_txt() {
  cat <<'EOF'
# BEGIN CodeAque boot menu (scripts/install-bootmenu.sh). Remove with --undo.
GRUB_DISTRIBUTOR="CodeAque"
GRUB_DEFAULT=codeaque
GRUB_TIMEOUT_STYLE=menu
GRUB_TIMEOUT=10
GRUB_DISABLE_OS_PROBER=false
GRUB_GFXMODE=1920x1080,1366x768,auto
GRUB_GFXPAYLOAD_LINUX=keep
GRUB_TERMINAL_OUTPUT=gfxterm
GRUB_THEME="/boot/grub/themes/codeaque/theme.txt"
GRUB_CMDLINE_LINUX_DEFAULT="$GRUB_CMDLINE_LINUX_DEFAULT quiet splash"
# END CodeAque boot menu
EOF
}

menu_entry_script() {
  cat <<'EOF'
#!/bin/sh
# CodeAque: the "CodeAque" menu entry (this Linux, newest kernel) and a Recovery submenu.
# Written by scripts/install-bootmenu.sh. --undo removes it.
set -e
K=/boot/vmlinuz; I=/boot/initrd.img
[ -e "$K" ] || K=$(ls -1v /boot/vmlinuz-* 2>/dev/null | tail -n 1)
[ -e "$I" ] || I=/boot/initrd.img-${K#/boot/vmlinuz-}
[ -e "$K" ] || exit 0
ROOTUUID=$(grub-probe --target=fs_uuid / 2>/dev/null || true)
BOOTUUID=$(grub-probe --target=fs_uuid /boot 2>/dev/null || true)
KREL=$(grub-mkrelpath "$K" 2>/dev/null || echo "$K")
IREL=$(grub-mkrelpath "$I" 2>/dev/null || echo "$I")
if [ -n "$ROOTUUID" ]; then ROOTARG="root=UUID=$ROOTUUID"; else ROOTARG="root=$(grub-probe --target=device / 2>/dev/null)"; fi
entry() {
  echo "menuentry \"$1\" --class $2 --id $3 {"
  echo "    load_video"
  echo "    set gfxpayload=keep"
  echo "    insmod gzio"
  echo "    insmod part_gpt"
  echo "    insmod part_msdos"
  echo "    insmod ext2"
  [ -n "$BOOTUUID" ] && echo "    search --no-floppy --fs-uuid --set=root $BOOTUUID"
  echo "    linux $KREL $ROOTARG ro $4"
  [ -e "$I" ] && echo "    initrd $IREL"
  echo "}"
}
entry "CodeAque" codeaque codeaque "${GRUB_CMDLINE_LINUX} ${GRUB_CMDLINE_LINUX_DEFAULT}"
echo "submenu \"Recovery\" --id codeaque-recovery {"
entry "CodeAque (recovery)" recovery codeaque-recovery-mode "recovery nomodeset ${GRUB_CMDLINE_LINUX}"
# Older kernels, if any, stay in this submenu so the main menu stays short.
# Compare real paths so the current kernel (often /boot/vmlinuz -> vmlinuz-VERSION) is not listed twice.
MAIN=$(readlink -f "$K" 2>/dev/null || echo "$K")
for k in /boot/vmlinuz-*; do
  [ -e "$k" ] || continue
  case "$k" in
    *.old) continue ;;
  esac
  kreal=$(readlink -f "$k" 2>/dev/null || echo "$k")
  [ "$kreal" = "$MAIN" ] && continue
  ver=${k#/boot/vmlinuz-}
  IOLD=/boot/initrd.img-$ver
  KOLD=$(grub-mkrelpath "$k" 2>/dev/null || echo "$k")
  IOLDREL=$(grub-mkrelpath "$IOLD" 2>/dev/null || echo "$IOLD")
  echo "menuentry \"CodeAque $ver\" --class codeaque {"
  echo "    load_video"
  echo "    set gfxpayload=keep"
  echo "    insmod gzio"
  echo "    insmod part_gpt"
  echo "    insmod part_msdos"
  echo "    insmod ext2"
  [ -n "$BOOTUUID" ] && echo "    search --no-floppy --fs-uuid --set=root $BOOTUUID"
  echo "    linux $KOLD $ROOTARG ro ${GRUB_CMDLINE_LINUX} ${GRUB_CMDLINE_LINUX_DEFAULT}"
  [ -e "$IOLD" ] && echo "    initrd $IOLDREL"
  echo "}"
  echo "menuentry \"CodeAque $ver (recovery)\" --class recovery {"
  echo "    load_video"
  echo "    set gfxpayload=keep"
  echo "    insmod gzio"
  echo "    insmod part_gpt"
  echo "    insmod part_msdos"
  echo "    insmod ext2"
  [ -n "$BOOTUUID" ] && echo "    search --no-floppy --fs-uuid --set=root $BOOTUUID"
  echo "    linux $KOLD $ROOTARG ro recovery nomodeset ${GRUB_CMDLINE_LINUX}"
  [ -e "$IOLD" ] && echo "    initrd $IOLDREL"
  echo "}"
done
echo "}"
EOF
}

windows_entry_script() {
  cat <<'EOF'
#!/bin/sh
# CodeAque: a "Windows" entry. Finds Windows' own boot manager at start-up with
# search --file, so it does not depend on partition numbers. Nothing on Windows is changed.
set -e
cat <<'GRUBEOF'
menuentry "Windows" --class windows --id windows {
    insmod part_gpt
    insmod fat
    insmod chain
    search --no-floppy --file --set=root /EFI/Microsoft/Boot/bootmgfw.efi
    chainloader /EFI/Microsoft/Boot/bootmgfw.efi
}
GRUBEOF
EOF
}

mac_entry_script() {
  cat <<'EOF'
#!/bin/sh
# CodeAque: a "Mac" entry. Chainloads OpenCore by searching for its file across
# EFI partitions at start-up, so it does not depend on partition numbers.
# Nothing on the Mac disk is changed.
set -e
cat <<'GRUBEOF'
menuentry "Mac" --class macos --id mac {
    insmod part_gpt
    insmod fat
    insmod chain
    if search --no-floppy --file --set=root /EFI/OC/OpenCore.efi ; then
        chainloader /EFI/OC/OpenCore.efi
    elif search --no-floppy --file --set=root /EFI/OC/OpenCoreX64.efi ; then
        chainloader /EFI/OC/OpenCoreX64.efi
    else
        echo "OpenCore was not found on any disk."
        sleep --interruptible 5
    fi
}
GRUBEOF
EOF
}

plymouth_theme_txt() {
  cat <<EOF
[Plymouth Theme]
Name=CodeAque
Description=Code's CodeAque splash with a live red loading bar
ModuleName=script

[script]
ImageDir=${PLY_DIR}
ScriptFile=${PLY_DIR}/codeaque.script
EOF
}

plymouth_script_txt() {
  cat <<'EOF'
// CodeAque splash: Code's red / chrome splash fills the screen; its loading bar fills
// with the real boot progress, one of the four dots is lit at a time, and the red glow pulses slowly.
ANIM = 1;
Window.SetBackgroundTopColor(0, 0, 0);
Window.SetBackgroundBottomColor(0, 0, 0);
W = Window.GetWidth(); H = Window.GetHeight(); X = Window.GetX(); Y = Window.GetY();
bg.image = Image("background-1920x1080.png");
k = Math.Max(W / bg.image.GetWidth(), H / bg.image.GetHeight());
bw = bg.image.GetWidth() * k; bh = bg.image.GetHeight() * k;
bx = X + W / 2 - bw / 2; by = Y + H / 2 - bh / 2;
bg.image = bg.image.Scale(bw, bh);
bg.sprite = Sprite(bg.image); bg.sprite.SetX(bx); bg.sprite.SetY(by); bg.sprite.SetZ(0);
glow.image = Image("code-glow.png").Scale(bw, bh);
glow.sprite = Sprite(glow.image); glow.sprite.SetX(bx); glow.sprite.SetY(by); glow.sprite.SetZ(5);
bar_x = bx + bw * 523 / 1672; bar_y = by + bh * 682 / 941; bar_w = bw * 627 / 1672; bar_h = Math.Max(3, bh * 19 / 941);
track.image = Image("code-track.png").Scale(bar_w, bar_h);
track.sprite = Sprite(track.image); track.sprite.SetX(bar_x); track.sprite.SetY(bar_y); track.sprite.SetZ(6); track.sprite.SetOpacity(0);
line.base = Image("code-line.png");
line.sprite = Sprite(); line.sprite.SetX(bar_x); line.sprite.SetY(bar_y); line.sprite.SetZ(7);
head.image = Image("code-head.png").Scale(bar_h, bar_h);
head.sprite = Sprite(head.image); head.sprite.SetY(bar_y); head.sprite.SetX(bar_x); head.sprite.SetZ(8);
dsz = Math.Max(3, bh * 19 / 941);
dimg = Image("dot.png").Scale(dsz, dsz);
for (j = 0; j < 4; j++) {
  dot[j].sprite = Sprite(dimg);
  dot[j].sprite.SetX(bx + bw * (757 + 46 * j) / 1672); dot[j].sprite.SetY(by + bh * 741 / 941); dot[j].sprite.SetZ(6);
  dot[j].sprite.SetOpacity(0);
}
t = 0; fade = 0; quitting = 0; progress = 0;
fun refresh() {
  t++;
  if (quitting) fade = Math.Max(0, fade - 0.05); else fade = Math.Min(1, fade + 0.03);
  bg.sprite.SetOpacity(fade); line.sprite.SetOpacity(fade); head.sprite.SetOpacity(fade);
  if (ANIM) glow.sprite.SetOpacity(fade * (0.15 + 0.3 * (0.5 + 0.5 * Math.Sin(t / 40)))); else glow.sprite.SetOpacity(fade * 0.2);
  lit = Math.Int(t / 20) % 4;
  for (j = 0; j < 4; j++) { if (j == lit || ANIM == 0 && j == 0) dot[j].sprite.SetOpacity(fade); else dot[j].sprite.SetOpacity(0); }
}
Plymouth.SetRefreshFunction(refresh);
fun progress_cb(duration, p) {
  if (p > progress) progress = p;
  w = Math.Max(1, Math.Int(bar_w * progress));
  line.sprite.SetImage(line.base.Scale(w, bar_h));
  head.sprite.SetX(bar_x + w - head.image.GetWidth() / 2);
}
Plymouth.SetBootProgressFunction(progress_cb);
fun quit_cb() { quitting = 1; }
Plymouth.SetQuitFunction(quit_cb);
EOF
}

# ---------------------------------------------------------------------------
# Assets
# ---------------------------------------------------------------------------

here_dir() {
  if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "${BASH_SOURCE[0]}" ]; then
    (cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  else
    printf ''
  fi
}

resolve_assets() {
  if [ -n "$ASSET_DIR" ] && [ -f "$ASSET_DIR/background.png" ]; then
    return 0
  fi
  local here
  here="$(here_dir)"
  if [ -n "$here" ] && [ -f "$here/bootmenu-theme/background.png" ]; then
    ASSET_DIR="$here/bootmenu-theme"
    PLY_ASSET_DIR="$(cd "$here/.." && pwd)/atarilauncher/bootscreen"
    return 0
  fi
  return 1
}

download_assets() {
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  say "Downloading CodeAque boot art ..."
  mkdir -p "$tmp/theme" "$tmp/ply"
  local f
  for f in background.png background-1366x768.png theme.txt \
           select_c.png select_n.png select_s.png select_e.png select_w.png \
           select_ne.png select_nw.png select_se.png select_sw.png; do
    curl -fsSL -o "$tmp/theme/$f" "${REPO_RAW}/${THEME_DIR_REL}/$f"
  done
  for f in background-1920x1080.png code-glow.png code-line.png code-track.png code-head.png dot.png blank.png; do
    curl -fsSL -o "$tmp/ply/$f" "${REPO_RAW}/${PLY_SRC_REL}/$f"
  done
  ASSET_DIR="$tmp/theme"
  PLY_ASSET_DIR="$tmp/ply"
  # Keep the temp dir alive until the script exits (RETURN would delete too soon).
  trap - RETURN
  ASSET_TMP="$tmp"
}

ensure_assets() {
  PLY_ASSET_DIR="${PLY_ASSET_DIR:-}"
  if resolve_assets; then
    if [ -z "${PLY_ASSET_DIR:-}" ] || [ ! -f "${PLY_ASSET_DIR}/background-1920x1080.png" ]; then
      local here
      here="$(here_dir)"
      if [ -n "$here" ] && [ -f "$here/../atarilauncher/bootscreen/background-1920x1080.png" ]; then
        PLY_ASSET_DIR="$(cd "$here/../atarilauncher/bootscreen" && pwd)"
      fi
    fi
    if [ -n "${PLY_ASSET_DIR:-}" ] && [ -f "${PLY_ASSET_DIR}/background-1920x1080.png" ]; then
      return 0
    fi
  fi
  download_assets
}

# ---------------------------------------------------------------------------
# Detect Windows / OpenCore without mounting anything
# ---------------------------------------------------------------------------

windows_seen() {
  local f m
  for f in \
      "$(p /boot/efi)/EFI/Microsoft/Boot/bootmgfw.efi" \
      "$(p /boot/efi)/EFI/microsoft/boot/bootmgfw.efi"; do
    [ -f "$f" ] && return 0
  done
  # Already-mounted vfat / EFI folders only — this script never mounts a disk.
  if have findmnt; then
    while IFS= read -r m; do
      [ -f "$m/EFI/Microsoft/Boot/bootmgfw.efi" ] && return 0
    done < <(findmnt -n -o TARGET -t vfat,fat,fat32 2>/dev/null || true)
  fi
  return 1
}

mac_seen() {
  local m
  if have findmnt; then
    while IFS= read -r m; do
      if [ -f "$m/EFI/OC/OpenCore.efi" ] || [ -f "$m/EFI/OC/OpenCoreX64.efi" ]; then
        return 0
      fi
    done < <(findmnt -n -o TARGET -t vfat,fat,fat32 2>/dev/null || true)
  fi
  for m in "$(p /boot/efi)" "$(p /efi)"; do
    if [ -f "$m/EFI/OC/OpenCore.efi" ] || [ -f "$m/EFI/OC/OpenCoreX64.efi" ]; then
      return 0
    fi
  done
  return 1
}

# ---------------------------------------------------------------------------
# Backup / restore helpers
# ---------------------------------------------------------------------------

backup_once() {
  local bk
  bk="$(p "$BACKUP_DIR")"
  if [ -f "$bk/done" ]; then
    return 0
  fi
  mkdir -p "$bk"
  if [ -f "$(p "$GRUB_DEFAULT")" ]; then
    cp -a "$(p "$GRUB_DEFAULT")" "$bk/grub"
  fi
  if [ -f "$(p "$GRUB_D_CFG")" ]; then
    cp -a "$(p "$GRUB_D_CFG")" "$bk/zzz-codeaque-boot.cfg"
  fi
  if [ -d "$(p "$GRUB_THEME_DIR")" ]; then
    cp -a "$(p "$GRUB_THEME_DIR")" "$bk/theme"
  fi
  if [ -f "$(p /etc/grub.d/40_custom)" ]; then
    cp -a "$(p /etc/grub.d/40_custom)" "$bk/40_custom"
  fi
  local g b
  for g in "$LINUX_SCRIPT" "$OSPROBER_SCRIPT" "$UEFI_SCRIPT"; do
    [ -e "$(p "$g")" ] || continue
    b="$bk/mode-$(basename "$g")"
    stat -c %a "$(p "$g")" > "$b"
  done
  if have plymouth-set-default-theme; then
    plymouth-set-default-theme > "$bk/plymouth" 2>/dev/null || true
  fi
  if [ -f "$(p /etc/plymouth/plymouthd.conf)" ]; then
    cp -a "$(p /etc/plymouth/plymouthd.conf)" "$bk/plymouthd.conf"
  fi
  touch "$bk/done"
}

disable_distro_entries() {
  # Hide Mint's own "Linux Mint" / "Advanced options" / UEFI firmware lines so the
  # menu is just CodeAque, Windows, Mac and Recovery. Scripts are made non-executable,
  # not deleted, and --undo puts the old mode back. 30_os-prober is switched off so
  # Windows is not listed twice; our Windows / Mac entries use search --file instead
  # (more reliable on a disk with several EFI partitions).
  local bk g b
  bk="$(p "$BACKUP_DIR")"
  mkdir -p "$bk"
  for g in "$LINUX_SCRIPT" "$OSPROBER_SCRIPT" "$UEFI_SCRIPT"; do
    [ -e "$(p "$g")" ] || continue
    b="$bk/mode-$(basename "$g")"
    [ -e "$b" ] || stat -c %a "$(p "$g")" > "$b"
    chmod 644 "$(p "$g")"
  done
}

restore_distro_entries() {
  local bk g b
  bk="$(p "$BACKUP_DIR")"
  for g in "$LINUX_SCRIPT" "$OSPROBER_SCRIPT" "$UEFI_SCRIPT"; do
    [ -e "$(p "$g")" ] || continue
    b="$bk/mode-$(basename "$g")"
    if [ -s "$b" ]; then
      chmod "$(cat "$b")" "$(p "$g")"
    fi
  done
}

# ---------------------------------------------------------------------------
# Install
# ---------------------------------------------------------------------------

install_theme() {
  local th
  th="$(p "$GRUB_THEME_DIR")"
  mkdir -p "$th"
  cp "$ASSET_DIR/background.png" "$th/background.png"
  if [ -f "$ASSET_DIR/background-1366x768.png" ]; then
    cp "$ASSET_DIR/background-1366x768.png" "$th/background-1366x768.png"
  fi
  cp "$ASSET_DIR"/select_*.png "$th/"
  local side
  for side in n s e w ne nw se sw c; do
    cp "$ASSET_DIR/select_c.png" "$th/terminal_box_${side}.png"
  done
  theme_txt > "$th/theme.txt"
  if have grub-mkfont; then
    local font s
    for font in /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf; do
      if [ -f "$font" ]; then
        for s in 20 24 36; do
          grub-mkfont -n CodeAque -s "$s" -o "$th/codeaque-$s.pf2" "$font" >/dev/null 2>&1 || true
        done
        break
      fi
    done
  fi
}

install_grub_scripts() {
  mkdir -p "$(dirname "$(p "$GRUB_D_CFG")")" "$(p /etc/grub.d)"
  grub_defaults_txt > "$(p "$GRUB_D_CFG")"
  chmod 644 "$(p "$GRUB_D_CFG")"
  menu_entry_script > "$(p "$MENU_SCRIPT")"
  windows_entry_script > "$(p "$WIN_SCRIPT")"
  mac_entry_script > "$(p "$MAC_SCRIPT")"
  chmod 755 "$(p "$MENU_SCRIPT")" "$(p "$WIN_SCRIPT")" "$(p "$MAC_SCRIPT")"
  # Also set the same keys in /etc/default/grub so they are visible there, without
  # losing any other settings. The grub.d file is what update-grub actually uses last.
  local dg
  dg="$(p "$GRUB_DEFAULT")"
  if [ -f "$dg" ]; then
    # Comment previous assignments so our grub.d file is not fighting a later one.
    sed -i \
      -e 's/^GRUB_TIMEOUT_STYLE=/#&/' \
      -e 's/^GRUB_TIMEOUT=/#&/' \
      -e 's/^GRUB_DISABLE_OS_PROBER=/#&/' \
      -e 's/^GRUB_GFXMODE=/#&/' \
      -e 's/^GRUB_THEME=/#&/' \
      -e 's/^GRUB_DEFAULT=/#&/' \
      "$dg"
    if ! grep -q 'GRUB_DISABLE_OS_PROBER=false' "$dg"; then
      printf '\n# Set by CodeAque boot menu (see also %s)\nGRUB_DISABLE_OS_PROBER=false\n' "$GRUB_D_CFG" >> "$dg"
    fi
  fi
}

install_plymouth() {
  PLYMOUTH_STATUS="skipped"
  if [ -z "${PLY_ASSET_DIR:-}" ] || [ ! -f "${PLY_ASSET_DIR}/background-1920x1080.png" ]; then
    PLYMOUTH_STATUS="skipped (splash pictures were not available)"
    return 0
  fi
  if ! have plymouth-set-default-theme && [ ! -d "$(p /usr/share/plymouth/themes)" ]; then
    PLYMOUTH_STATUS="skipped (Plymouth is not installed)"
    return 0
  fi
  local ply
  ply="$(p "$PLY_DIR")"
  mkdir -p "$ply"
  cp "${PLY_ASSET_DIR}/background-1920x1080.png" \
     "${PLY_ASSET_DIR}/code-glow.png" \
     "${PLY_ASSET_DIR}/code-line.png" \
     "${PLY_ASSET_DIR}/code-track.png" \
     "${PLY_ASSET_DIR}/code-head.png" \
     "${PLY_ASSET_DIR}/dot.png" \
     "$ply/"
  plymouth_script_txt > "$ply/codeaque.script"
  plymouth_theme_txt > "$ply/codeaque.plymouth"
  if [ -n "$ROOT" ]; then
    PLYMOUTH_STATUS="prepared (not activated, fake root)"
    return 0
  fi
  if have plymouth-set-default-theme; then
    plymouth-set-default-theme codeaque >/dev/null 2>&1 || true
  fi
  if have update-alternatives && [ -d /etc/alternatives ]; then
    update-alternatives --install /usr/share/plymouth/themes/default.plymouth default.plymouth \
      "$PLY_DIR/codeaque.plymouth" 310 >/dev/null 2>&1 || true
    update-alternatives --set default.plymouth "$PLY_DIR/codeaque.plymouth" >/dev/null 2>&1 || true
  fi
  local pc
  pc="$(p /etc/plymouth/plymouthd.conf)"
  mkdir -p "$(dirname "$pc")"
  grep -q '^\[Daemon\]' "$pc" 2>/dev/null || printf '[Daemon]\n' >> "$pc"
  sed -i '/^Theme=/d;/^ShowDelay=/d;/^DeviceTimeout=/d' "$pc"
  sed -i 's/^\[Daemon\]$/[Daemon]\nTheme=codeaque\nShowDelay=0\nDeviceTimeout=8/' "$pc"
  if have update-initramfs; then
    if update-initramfs -u -k all >/dev/null 2>&1; then
      PLYMOUTH_STATUS="installed (Mint logo hidden on the loading screen)"
    else
      PLYMOUTH_STATUS="theme copied, but updating the loading screen image failed (the menu is still installed)"
    fi
  else
    PLYMOUTH_STATUS="theme copied (update-initramfs was not available)"
  fi
}

run_update_grub() {
  if [ -n "$SKIP_UPDATE" ] || [ -n "$ROOT" ]; then
    # Produce a minimal grub.cfg the tests can read, without touching a real boot loader.
    local cfg
    cfg="$(p "$GRUB_CFG")"
    mkdir -p "$(dirname "$cfg")"
    {
      echo "set theme=${GRUB_THEME_DIR}/theme.txt"
      echo "set timeout=10"
      echo "set timeout_style=menu"
      echo "set default=codeaque"
      # Run our entry scripts if the tools they need exist; otherwise write stubs.
      if have grub-probe && [ -e /boot/vmlinuz ] || ls /boot/vmlinuz-* >/dev/null 2>&1; then
        GRUB_CMDLINE_LINUX="${GRUB_CMDLINE_LINUX:-}" GRUB_CMDLINE_LINUX_DEFAULT="${GRUB_CMDLINE_LINUX_DEFAULT:-quiet splash}" \
          sh "$(p "$MENU_SCRIPT")" || true
      else
        cat <<'STUB'
menuentry "CodeAque" --class codeaque --id codeaque {
    linux /vmlinuz root=/dev/sda10 ro quiet splash
    initrd /initrd.img
}
submenu "Recovery" --id codeaque-recovery {
menuentry "CodeAque (recovery)" --class recovery --id codeaque-recovery-mode {
    linux /vmlinuz root=/dev/sda10 ro recovery nomodeset
    initrd /initrd.img
}
}
STUB
      fi
      sh "$(p "$WIN_SCRIPT")"
      sh "$(p "$MAC_SCRIPT")"
    } > "$cfg"
    return 0
  fi
  if ! have update-grub; then
    err "update-grub was not found. This installer is for Linux Mint / Ubuntu with GRUB."
    return 1
  fi
  update-grub
}

summarise() {
  local cfg win_found mac_found win_entry mac_entry ca_entry rec_entry theme_line
  cfg="$(p "$GRUB_CFG")"
  win_found=no
  mac_found=no
  windows_seen && win_found=yes
  mac_seen && mac_found=yes
  win_entry=no
  mac_entry=no
  ca_entry=no
  rec_entry=no
  theme_line=no
  if [ -f "$cfg" ]; then
    grep -q 'menuentry "Windows"' "$cfg" && win_entry=yes
    grep -q 'menuentry "Mac"' "$cfg" && mac_entry=yes
    grep -q 'menuentry "CodeAque"' "$cfg" && ca_entry=yes
    grep -q 'submenu "Recovery"' "$cfg" && rec_entry=yes
    grep -q 'themes/codeaque' "$cfg" && theme_line=yes
  fi
  cat <<EOF

----------------------------------------
CodeAque boot menu is installed.
----------------------------------------
The start-up menu shows:

  • CodeAque     (this Linux, starts after 10 seconds if you do nothing)
  • Windows      (finds Windows' boot manager at start-up)
  • Mac          (finds OpenCore at start-up)
  • Recovery     (a submenu — recovery mode and older kernels)

CodeAque entry:     $ca_entry
Windows entry:      $win_entry
Mac entry:          $mac_entry
Recovery submenu:   $rec_entry
Theme in grub.cfg:  $theme_line

Windows boot file seen on a mounted EFI during install:  $win_found
OpenCore file seen on a mounted EFI during install:      $mac_found
Loading screen (Plymouth):  ${PLYMOUTH_STATUS:-not run}

If Windows or OpenCore was not seen during install, that is often normal —
those files live on EFI partitions this script does not mount. The menu
entries still search for them when the computer starts.

This script did not change the firmware boot order and did not install
GRUB onto any other disk. If the computer still goes straight into
Windows or the OpenCore picker, restart, tap Del, set Boot Option #1
to "ubuntu", then press F10 to save.

To put the old menu back:
  curl -fsSL ${REPO_RAW}/scripts/install-bootmenu.sh | sudo bash -s -- --undo
EOF
  # Non-zero if the essential entries did not land, so a piped install is noticeable.
  if [ "$ca_entry" != yes ] || [ "$win_entry" != yes ] || [ "$mac_entry" != yes ]; then
    err "The menu was written, but not every expected entry was found in grub.cfg."
    return 1
  fi
  return 0
}

do_install() {
  if [ -z "$ROOT" ] && [ -z "$SKIP_UPDATE" ]; then
    if ! have update-grub && ! have grub-mkconfig; then
      err "This computer does not look like it uses GRUB (update-grub is missing)."
      exit 1
    fi
  fi
  ensure_assets
  backup_once
  install_theme
  install_grub_scripts
  disable_distro_entries
  run_update_grub
  install_plymouth
  summarise
}

# ---------------------------------------------------------------------------
# Undo
# ---------------------------------------------------------------------------

do_undo() {
  local bk th ply
  bk="$(p "$BACKUP_DIR")"
  th="$(p "$GRUB_THEME_DIR")"
  ply="$(p "$PLY_DIR")"
  if [ ! -f "$bk/done" ] && [ ! -f "$bk/grub" ]; then
    err "No backup was found at $BACKUP_DIR, so there is nothing to undo."
    exit 1
  fi
  rm -f "$(p "$GRUB_D_CFG")" "$(p "$MENU_SCRIPT")" "$(p "$WIN_SCRIPT")" "$(p "$MAC_SCRIPT")"
  rm -rf "$th" "$ply"
  restore_distro_entries
  if [ -f "$bk/grub" ]; then
    cp -a "$bk/grub" "$(p "$GRUB_DEFAULT")"
  fi
  if [ -d "$bk/theme" ]; then
    mkdir -p "$(dirname "$th")"
    cp -a "$bk/theme" "$th"
  fi
  if [ -f "$bk/plymouthd.conf" ]; then
    mkdir -p "$(dirname "$(p /etc/plymouth/plymouthd.conf)")"
    cp -a "$bk/plymouthd.conf" "$(p /etc/plymouth/plymouthd.conf)"
  fi
  if [ -z "$ROOT" ]; then
    if have update-alternatives; then
      update-alternatives --remove default.plymouth "$PLY_DIR/codeaque.plymouth" >/dev/null 2>&1 || true
    fi
    if [ -s "$bk/plymouth" ] && have plymouth-set-default-theme; then
      plymouth-set-default-theme "$(cat "$bk/plymouth")" >/dev/null 2>&1 || true
    fi
    if [ -z "$SKIP_UPDATE" ]; then
      have update-grub && update-grub >/dev/null 2>&1 || true
      have update-initramfs && update-initramfs -u -k all >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "$bk"
  say "The CodeAque boot menu has been removed and the previous GRUB settings have been put back."
}

# ---------------------------------------------------------------------------
# Self-test (no root, no real boot loader)
# ---------------------------------------------------------------------------

do_selftest() {
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" EXIT
  mkdir -p "$tmp/etc/default/grub.d" "$tmp/etc/grub.d" "$tmp/boot/grub" "$tmp/etc/plymouth"
  printf 'GRUB_DEFAULT=0\nGRUB_TIMEOUT=0\nGRUB_TIMEOUT_STYLE=hidden\n' > "$tmp/etc/default/grub"
  printf '#!/bin/sh\n' > "$tmp/etc/grub.d/10_linux"
  printf '#!/bin/sh\n' > "$tmp/etc/grub.d/30_os-prober"
  printf '#!/bin/sh\n' > "$tmp/etc/grub.d/30_uefi-firmware"
  chmod 755 "$tmp/etc/grub.d/"*
  local here
  here="$(here_dir)"
  if [ -z "$here" ] || [ ! -f "$here/bootmenu-theme/background.png" ]; then
    err "Self-test needs to be run from a CodeAque checkout (scripts/bootmenu-theme is missing)."
    exit 1
  fi
  CODEAQUE_BOOTMENU_ROOT="$tmp" CODEAQUE_BOOTMENU_ASSETS="$here/bootmenu-theme" \
    CODEAQUE_BOOTMENU_SKIP_UPDATE=1 bash "$here/install-bootmenu.sh" >/tmp/codeaque-bootmenu-selftest.out
  local cfg
  cfg="$tmp/boot/grub/grub.cfg"
  grep -q 'menuentry "CodeAque"' "$cfg"
  grep -q 'menuentry "Windows"' "$cfg"
  grep -q 'menuentry "Mac"' "$cfg"
  grep -q 'submenu "Recovery"' "$cfg"
  grep -q 'search --no-floppy --file --set=root /EFI/Microsoft/Boot/bootmgfw.efi' "$cfg"
  grep -q 'search --no-floppy --file --set=root /EFI/OC/OpenCore.efi' "$cfg"
  grep -q 'chainloader /EFI/OC/OpenCore.efi' "$cfg"
  grep -q 'themes/codeaque' "$cfg"
  test -f "$tmp/boot/grub/themes/codeaque/theme.txt"
  test -f "$tmp/boot/grub/themes/codeaque/background.png"
  grep -q 'Starting CodeAque in %ds' "$tmp/boot/grub/themes/codeaque/theme.txt"
  grep -q 'GRUB_TIMEOUT=10' "$tmp/etc/default/grub.d/zzz-codeaque-boot.cfg"
  grep -q 'GRUB_DISABLE_OS_PROBER=false' "$tmp/etc/default/grub.d/zzz-codeaque-boot.cfg"
  test ! -x "$tmp/etc/grub.d/10_linux"
  test ! -x "$tmp/etc/grub.d/30_os-prober"
  test ! -x "$tmp/etc/grub.d/30_uefi-firmware"
  test -x "$tmp/etc/grub.d/06_codeaque"
  test -x "$tmp/etc/grub.d/35_codeaque_windows"
  test -x "$tmp/etc/grub.d/36_codeaque_mac"
  test -f "$tmp/var/lib/codeaque-bootmenu/grub"
  # Idempotent: second run keeps the first backup.
  CODEAQUE_BOOTMENU_ROOT="$tmp" CODEAQUE_BOOTMENU_ASSETS="$here/bootmenu-theme" \
    CODEAQUE_BOOTMENU_SKIP_UPDATE=1 bash "$here/install-bootmenu.sh" >/dev/null
  grep -q 'GRUB_DEFAULT=0' "$tmp/var/lib/codeaque-bootmenu/grub"
  # Undo puts things back.
  CODEAQUE_BOOTMENU_ROOT="$tmp" CODEAQUE_BOOTMENU_ASSETS="$here/bootmenu-theme" \
    CODEAQUE_BOOTMENU_SKIP_UPDATE=1 bash "$here/install-bootmenu.sh" --undo >/dev/null
  test ! -e "$tmp/etc/grub.d/06_codeaque"
  test ! -e "$tmp/boot/grub/themes/codeaque"
  test -x "$tmp/etc/grub.d/10_linux"
  test -x "$tmp/etc/grub.d/30_os-prober"
  grep -q 'GRUB_TIMEOUT_STYLE=hidden' "$tmp/etc/default/grub"
  # Safety: the installer must never invoke disk-changing tools.
  if awk 'BEGIN{e=0} /^[[:space:]]*(grub-install|efibootmgr|parted|sgdisk|mkfs|fdisk|ntfsresize)([[:space:]]|$)/{e=1} END{exit e}' "$here/install-bootmenu.sh"; then
    :
  else
    err "Self-test: installer source would run a disk-changing command."
    exit 1
  fi
  say "Self-test passed."
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

ASSET_TMP=""
cleanup() {
  if [ -n "${ASSET_TMP:-}" ] && [ -d "${ASSET_TMP:-}" ]; then
    rm -rf "$ASSET_TMP"
  fi
}
trap cleanup EXIT

need_root "${1:-}"
case "${1:-}" in
  --help|-h) usage ;;
  --undo) do_undo ;;
  --selftest) do_selftest ;;
  ""|--install) do_install ;;
  *)
    err "Unknown option: $1"
    usage
    exit 2
    ;;
esac
