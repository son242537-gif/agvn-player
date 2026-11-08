#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-winhandler.exe 5's AGVN_STATE_TEXT_INPUT in Wine on a PC with typer.exe, which turns its text input off
# and on as SDL (Ren'Py) does, then focuses another window. Pass: "STATE 4" only while the "on" phase after the first
# "off" holds (from about 4.5 s to 7 s); not at the start, not for the 0.1 s blink, not for the other window. Needs
# wine64 (9.x), xvfb-run, mingw-w64 and python3.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$here/../../../.."
work="$(mktemp -d)"
x86_64-w64-mingw32-gcc -O2 -mwindows -o "$work/typer.exe" "$here/typer.c" -limm32
wine=$(command -v wine64 || echo /usr/lib/wine/wine64)
server=$(command -v wineserver || echo /usr/lib/wine/wineserver)
export WINEPREFIX="$work/prefix" WINEDEBUG=-all
cd "$work"
xvfb-run -a -s "-screen 0 1280x720x24" bash -c "
  '$wine' wineboot -i >/dev/null 2>&1
  python3 -I '$here/app_side.py' > app_side.out 2>&1 &
  sleep 0.5
  '$wine' '$repo/app/src/main/assets/agvn/agvn-winhandler.exe' typer.exe &
  sleep 15; '$server' -k; wait" >/dev/null 2>&1 || true
grep -v "relative moves" app_side.out
echo "---- typer.log"
cat typer.log
