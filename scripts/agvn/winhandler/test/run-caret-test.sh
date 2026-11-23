#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-winhandler.exe 6's caret rule in Wine on a PC with caret.exe: a caret in the game's own window (KiriKiri,
# Unity, GameMaker) is no text field; one in an EDIT control is. Pass: "STATE 2" only while the edit field has the
# focus (from about 3 s to 6 s), "STATE 0" before and after. Needs wine64 (9.x), xvfb-run, mingw-w64 and python3.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$here/../../../.."
work="$(mktemp -d)"
x86_64-w64-mingw32-gcc -O2 -mwindows -o "$work/caret.exe" "$here/caret.c"
wine=$(command -v wine64 || echo /usr/lib/wine/wine64)
server=$(command -v wineserver || echo /usr/lib/wine/wineserver)
export WINEPREFIX="$work/prefix" WINEDEBUG=-all
cd "$work"
xvfb-run -a -s "-screen 0 1280x720x24" bash -c "
  '$wine' wineboot -i >/dev/null 2>&1
  python3 -I '$here/app_side.py' > app_side.out 2>&1 &
  sleep 0.5
  '$wine' '$repo/app/src/main/assets/agvn/agvn-winhandler.exe' caret.exe &
  sleep 13; '$server' -k; wait" >/dev/null 2>&1 || true
grep -v "relative moves" app_side.out
echo "---- caret.log"
cat caret.log
