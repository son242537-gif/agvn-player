#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Runs syntaxgame.rpy (Python 2 syntax in .rpy source) on Ren'Py 8.5.3 on a PC, first with Ren'Py's own fixes, then
# with app/src/main/renpy/agvn_syntax.py as main.py installs it for every game. Pass: the first run ends in
# "leading zeros in decimal integer literals are not permitted" (Ren'Py's bug: "0.5" beside "02" becomes "0o.5"), the
# second compiles every block and lint ends with its statistics, no syntax error and no traceback (a function Ren'Py
# cannot pickle in its module backup would stop every game). Ren'Py 8.5.3's SDK comes from app/agvn-downloads
# (./gradlew downloadRenpy).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
work="$(mktemp -d)"
export SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy AGVN_NO_PY2=1
tar -xjf "$repo/app/agvn-downloads/renpy-8.5.3-sdk.tar.bz2" -C "$work"
rp8="$work/renpy-8.5.3-sdk"
for run in renpy agvn; do
    game="$work/$run"
    mkdir -p "$game/game"
    cp "$here/syntaxgame.rpy" "$game/game/script.rpy"
    AGVN_NO_SYNTAX=$([ $run = renpy ] && echo 1 || echo 0) AGVN_RENPY_DIR="$repo/app/src/main/renpy" RENPY_PY="$rp8/renpy.py" \
        "$rp8/lib/py3-linux-x86_64/python" "$here/py2-harness.py" "$game" lint > "$work/$run.lint" 2>&1 || true
    echo "-- $run: $(grep -c "leading zeros\|invalid syntax" "$work/$run.lint" || true) syntax errors," \
        "$(grep -c "Traceback" "$work/$run.lint" || true) tracebacks"
    grep -h -m2 "leading zeros\|invalid\|Error\|Statistics:" "$work/$run.lint" | sed 's/^/   /' || true
done
