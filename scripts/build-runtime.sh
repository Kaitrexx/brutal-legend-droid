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

mkdir -p "$CACHE" "$STAGE/bin" "$STAGE/assets"

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
  tar --no-same-owner --no-same-permissions -xmf "$CACHE/$p" -C "$STAGE"
done

# --- kiçiltmə: yalnız tək oyun üçün lazım olan hissələr qalır ---
GLIBC="$STAGE/glibc"
WINE="$GLIBC/wine-9.3-vanilla-wow64"

# 32-bit Windows DLL-ləri — Windows 64-bit profili üçün lazım deyil (223 MB)
rm -rf "$WINE/lib/wine/i386-windows"
# dev/docs/sanitizer/statik kitabxanalar
rm -rf "$GLIBC/include" "$GLIBC/share/doc" "$GLIBC/share/man" \
       "$GLIBC/share/locale" "$GLIBC/share/gtk-doc" "$GLIBC/opt/box"
find "$GLIBC/lib" -maxdepth 1 -type f \( \
  -name '*.a' -o -name 'libasan*' -o -name 'libtsan*' -o -name 'liblsan*' \
  -o -name 'libubsan*' -o -name 'libgfortran*' -o -name 'libvulkan_freedreno*' \
  \) -delete 2>/dev/null || true
# DXVK/Mesa variantları — yalnız biri
find "$GLIBC/opt/libs/d3d" -type f ! -name 'dxvk-async-1.10.3.7z' -delete
find "$GLIBC/opt/libs/mesa" -type f ! -name 'virgl-mesa-22.7z' -delete

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

# Termux:X11 loader — app_process com.termux.x11.Loader üçün
X11_DEB="https://packages.termux.dev/apt/termux-x11/pool/main/t/termux-x11-nightly/termux-x11-nightly_1.03.01-6_all.deb"
fetch "$X11_DEB" "termux-x11-nightly.deb"
mkdir -p "$CACHE/x11deb"
(cd "$CACHE/x11deb" && ar x "$CACHE/termux-x11-nightly.deb" && tar -xf data.tar.* --no-same-permissions)
cp "$CACHE/x11deb/data/data/com.termux/files/usr/libexec/termux-x11/loader.apk" "$STAGE/assets/loader.apk"

cat > "$STAGE/bin/termux-x11" <<'SCRIPT'
#!/system/bin/sh
RUNTIME_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export CLASSPATH="$RUNTIME_DIR/assets/loader.apk"
export TMPDIR="${TMPDIR:-$RUNTIME_DIR/tmp}"
mkdir -p "$TMPDIR/.X11-unix"
unset LD_LIBRARY_PATH LD_PRELOAD
exec /system/bin/app_process -Xnoimage-dex2oat / --nice-name="termux-x11" com.termux.x11.Loader "$@"
SCRIPT
chmod +x "$STAGE/bin/termux-x11"

cat > "$STAGE/bin/run-windows.sh" <<'SCRIPT'
#!/system/bin/sh
# Brutal Legend (Windows 64-bit) — Termux:X11 + Wine + Box64.
RUNTIME_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GLIBC="$RUNTIME_DIR/glibc"
GAME_DIR="$1"
LOG_FILE="${2:-$RUNTIME_DIR/emulator.log}"
FPS_FILE="${3:-$RUNTIME_DIR/fps.log}"
TMPDIR="$RUNTIME_DIR/tmp"
export TMPDIR
mkdir -p "$TMPDIR/.X11-unix"

echo "X server başladılır..." >>"$LOG_FILE"
"$RUNTIME_DIR/bin/termux-x11" :0 >>"$LOG_FILE" 2>&1 &
XPID=$!
sleep 2
am start --user 0 -n com.termux.x11/.MainActivity >>"$LOG_FILE" 2>&1 || true

"$RUNTIME_DIR/bin/proot" -R "$GLIBC" -0 -w /root \
  --bind "$GAME_DIR":/game \
  --bind "$TMPDIR/.X11-unix":/tmp/.X11-unix \
  /bin/bash -c '
    export DISPLAY=:0
    export WINEPREFIX=/root/.wine
    export BOX64_DYNAREC=1
    export BOX64_DYNAREC_BIGBLOCK=3
    export BOX64_DYNAREC_STRONGMEM=1
    export BOX64_DYNAREC_FASTNAN=1
    export DXVK_ASYNC=1
    export DXVK_HUD=fps
    mkdir -p "$WINEPREFIX"
    if [ ! -f "$WINEPREFIX/system.reg" ]; then
      echo "Wine prefix yaradılır (ilk açılış)..." >&2
      /bin/box64 /wine-9.3-vanilla-wow64/bin/wineboot -u >/dev/null 2>&1 || true
    fi
    cd /game || exit 1
    EXE=$(find . -maxdepth 3 -iname "BrutalLegend.exe" | head -1)
    if [ -z "$EXE" ]; then
      echo "BrutalLegend.exe tapılmadı: $GAME_DIR" >&2
      exit 2
    fi
    exec /bin/box64 /wine-9.3-vanilla-wow64/bin/wine "$EXE"
  ' >>"$LOG_FILE" 2>&1
RESULT=$?
kill "$XPID" 2>/dev/null || true
exit $RESULT
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
