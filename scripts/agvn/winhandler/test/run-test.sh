#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-winhandler.exe 2 in Wine on a PC: looker.exe holds the cursor as Unity's CursorLockMode.Locked does
# (hidden, clipped to its centre), lets it go, shows an edit box, then hides the cursor only, clips it only, and hides
# it confined to its window (a game drawing its own cursor).
# app_side.py plays the app: it prints the RC_AGVN_STATE reports and sends 20 finger moves of (7, -3) while the
# cursor is held. Pass: "STATE 1" during the hold, "STATE 2" during the edit box, 0 otherwise, and looker.log has
# "raw 7 -3" 20 times (no acceleration). Needs wine64 (9.x), xvfb-run, mingw-w64 and python3.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$here/../../../.."
work="$(mktemp -d)"
x86_64-w64-mingw32-gcc -O2 -mwindows -o "$work/looker.exe" "$here/looker.c"
wine=$(command -v wine64 || echo /usr/lib/wine/wine64)
server=$(command -v wineserver || echo /usr/lib/wine/wineserver)
export WINEPREFIX="$work/prefix" WINEDEBUG=-all
cd "$work"
xvfb-run -a -s "-screen 0 1280x720x24" bash -c "
  '$wine' wineboot -i >/dev/null 2>&1
  python3 -I '$here/app_side.py' > app_side.out 2>&1 &
  sleep 0.5
  '$wine' '$repo/app/src/main/assets/agvn/agvn-winhandler.exe' looker.exe &
  sleep 18.5; '$server' -k; wait" >/dev/null 2>&1 || true
cat app_side.out
echo "---- looker.log"
uniq -c looker.log
