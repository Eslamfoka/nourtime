#!/usr/bin/env bash
# Renders the Play feature graphic (1024x500 PNG) from feature-graphic.html with headless Edge/Chrome.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
browser="${BROWSER_EXE:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"
out="${1:-$here/feature-graphic.png}"
"$browser" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --allow-file-access-from-files --window-size=1024,500 \
  --screenshot="$(cygpath -w "$out")" "file:///$(cygpath -m "$here/feature-graphic.html")"
echo "$out"
