#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Runs Ren'Py 7 games (Python 2) on Ren'Py 8.5.3 with app/src/main/renpy/agvn_py2.py, on a PC, as "Chạy nhẹ" does for a
# Ren'Py 7 game on trial (AgvnRenpyPy2): Ren'Py 7.3.5's tutorial and the_question with their .rpyc files only, and a
# test game of Python 2's ways compiled by Ren'Py 7.3.5 itself. Pass: every lint ends without "Traceback" and the test
# game's log.txt says "AGVN-PY2 RESULTS" with every check True, as Ren'Py 7.3.5 does. Downloads Ren'Py 7.3.5's SDK
# (95 MB) from renpy.org; Ren'Py 8.5.3's comes from app/agvn-downloads (./gradlew downloadRenpy).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
work="$(mktemp -d)"
export SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy
curl -sS -o "$work/rp7.tar.bz2" https://www.renpy.org/dl/7.3.5/renpy-7.3.5-sdk.tar.bz2
tar -xjf "$work/rp7.tar.bz2" -C "$work"
tar -xjf "$repo/app/agvn-downloads/renpy-8.5.3-sdk.tar.bz2" -C "$work"
rp7="$work/renpy-7.3.5-sdk" rp8="$work/renpy-8.5.3-sdk"
mkdir -p "$work/py2game/game"
cp "$here/py2game.rpy" "$work/py2game/game/script.rpy"
"$rp7/renpy.sh" "$work/py2game" compile >/dev/null 2>&1
for g in tutorial the_question; do cp -r "$rp7/$g" "$work/$g"; done
rm -f "$work/tutorial/game/testcases.rpyc" # Ren'Py 8 reads no test case of Ren'Py 7 (a game has none)
for g in tutorial the_question py2game; do
    find "$work/$g" -name "*.rpy" -delete
    rm -rf "$work/$g/game/cache" "$work/$g/log.txt"
    AGVN_RENPY_DIR="$repo/app/src/main/renpy" RENPY_PY="$rp8/renpy.py" \
        "$rp8/lib/py3-linux-x86_64/python" "$here/py2-harness.py" "$work/$g" lint > "$work/$g.lint" 2>&1 || true
    echo "-- $g: $(grep -c Traceback "$work/$g.lint" || true) tracebacks"
done
grep -h "AGVN-PY2 RESULTS" "$work/py2game/log.txt"
