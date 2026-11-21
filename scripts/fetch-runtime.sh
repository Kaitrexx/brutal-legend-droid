#!/usr/bin/env bash
# Runtime bundle-in SHA256-sini dogrulayir ve yerel gelistirme uchun hazirlayir.
# Bundle itself GitHub Releases-de yayimlanir; bu script oyun fayllarini hech vaxt indirmir.
set -euo pipefail

MANIFEST="app/src/main/assets/runtime-manifest.json"
OUT="${1:-/tmp/brutal-legend-runtime.zip}"

command -v python3 >/dev/null || { echo "python3 lazimdir" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl lazimdir" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum lazimdir" >&2; exit 1; }

URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["runtime_bundle"]["url"])' "$MANIFEST")"
EXPECTED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["runtime_bundle"]["sha256"])' "$MANIFEST")"

if [[ "$EXPECTED" == REPLACE* || -z "$EXPECTED" ]]; then
  echo "XETA: runtime bundle SHA256 manifest-de doldurulmayib." >&2
  exit 1
fi

echo "Endirilir: $URL"
curl -fL --retry 3 -o "$OUT" "$URL"
ACTUAL="$(sha256sum "$OUT" | awk '{print $1}')"

if [[ "${ACTUAL,,}" != "${EXPECTED,,}" ]]; then
  echo "XETA: SHA256 uyusmur." >&2
  echo "  goclenen: $EXPECTED" >&2
  echo "  alinan:   $ACTUAL" >&2
  exit 1
fi

echo "OK: $ACTUAL"
echo "ZIP hazirdir: $OUT"
