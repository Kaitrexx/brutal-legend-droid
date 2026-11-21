#!/usr/bin/env bash
# Phase 2 runtime bundle: Mobox (open source) komponentlari + Termux proot + Termux:X11.
# Neticə: runtime-arm64.zip (SHA256 ilə runtime-manifest.json-a yazılır).
# Oyun faylları HEÇ VAXT daxil edilmir.
set -euo pipefail

WORK="${WORK:-/tmp/opencode/mobox}"
CACHE="$WORK/pkg"
STAGE="$WORK/stage"
OUT="${OUT:-$WORK/runtime-arm64.zip}"

GITLAB="https://gitlab.com/api/v4/projects/54240888/repository/files"
TERMUX="https://packages.termux.dev/apt/termux-main"
MOBOX_APK="https://raw.githubusercontent.com/olegos2/mobox/main/components/termux-x11.apk"

PACKAGES=(
  glibc-prefix.tar.xz
  box64-binaries.tar.xz
  wine-9.3-vanilla-wow64.tar.xz
  virgl-mesa.tar.xz
  dxvk.tar.xz
  scripts.tar.xz
)
DEBS=(
  pool/main/p/proot/proot_5.1.107.102_aarch64.deb
  pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb
)

command -v curl >/dev/null || { echo "curl lazimdir" >&2; exit 1; }
command -v tar >/dev/null || { echo "tar lazimdir" >&2; exit 1; }
command -v zip >/dev/null || { echo "zip lazimdir" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum lazimdir" >&2; exit 1; }

mkdir -p "$CACHE" "$STAGE/glibc" "$STAGE/bin" "$STAGE/assets"

fetch() {
  local url="$1" file="$2"
  if [ -s "$CACHE/$file" ]; then
    echo "cache: $file"
  else
    echo "download: $file"
    curl -fsSL --retry 3 --max-time 300 "$url" -o "$CACHE/$file"
  fi
}

for p in "${PACKAGES[@]}"; do
  enc=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$p")
  fetch "$GITLAB/$enc/raw?ref=main" "$p"
  echo "extract: $p"
  tar --no-same-owner --no-same-permissions -xmf "$CACHE/$p" -C "$STAGE/glibc"
done

for d in "${DEBS[@]}"; do
  f=$(basename "$d")
  fetch "$TERMUX/$d" "$f"
  mkdir -p "$CACHE/deb-$f"
  (cd "$CACHE/deb-$f" && ar x "$CACHE/$f" && tar -xf data.tar.* -C "$STAGE" --no-same-permissions)
done

mkdir -p "$STAGE/bin"
cp "$STAGE/data/data/com.termux/files/usr/bin/proot" "$STAGE/bin/proot"
cp "$STAGE/data/data/com.termux/files/usr/lib/libtalloc.so.2.5.0" "$STAGE/bin/libtalloc.so.2"
chmod +x "$STAGE/bin/proot"

fetch "$MOBOX_APK" "termux-x11.apk"
cp "$CACHE/termux-x11.apk" "$STAGE/assets/termux-x11.apk"

cat > "$STAGE/bin/run-windows.sh" <<'SCRIPT'
#!/system/bin/sh
# Brutal Legend (Windows 64-bit) — Mobox/Wine/Box64 üzərindən.
RUNTIME_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GLIBC="$RUNTIME_DIR/glibc"
GAME_DIR="$1"
LOG_FILE="${2:-$RUNTIME_DIR/emulator.log}"
FPS_FILE="${3:-$RUNTIME_DIR/fps.log}"

export WINE_PATH="$GLIBC/wine-9.3-vanilla-wow64"
export WINEPREFIX="$GLIBC/wineprefix"
export BOX64_DYNAREC=1
export BOX64_DYNAREC_BIGBLOCK=3
export BOX64_DYNAREC_STRONGMEM=1
export BOX64_DYNAREC_FASTNAN=1
export DXVK_ASYNC=1
export DXVK_HUD=fps
export DISPLAY=:0
export PATH="$GLIBC/bin:$WINE_PATH/bin:$PATH"

mkdir -p "$WINEPREFIX"

"$RUNTIME_DIR/bin/proot" -R "$GLIBC" -0 -w /root \
  --bind "$GAME_DIR":/game \
  /bin/bash -c '
    cd /game || exit 1
    EXE=$(find . -maxdepth 2 -iname "BrutalLegend.exe" | head -1)
    if [ -z "$EXE" ]; then
      echo "BrutalLegend.exe tapılmadı: $GAME_DIR" >&2
      exit 2
    fi
    exec "$WINE_PATH/bin/wine" "$EXE"
  ' >>"$LOG_FILE" 2>&1
SCRIPT
chmod +x "$STAGE/bin/run-windows.sh"

cat > "$STAGE/bin/run-linux.sh" <<'SCRIPT'
#!/system/bin/sh
# Linux 32-bit yolu: FEX/Box86 hazır olana qədər placeholder.
echo "Linux 32-bit profili hələ dəstəklənmir — windows-box64 profilini istifadə edin." >&2
exit 3
SCRIPT
chmod +x "$STAGE/bin/run-linux.sh"

echo "runtime-v0.1" > "$STAGE/version.txt"

rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" .)
echo "BUNDLE: $OUT ($(stat -c%s "$OUT") bytes)"
echo "SHA256: $(sha256sum "$OUT" | awk '{print $1}')"
