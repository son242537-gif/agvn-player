#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-mlang64.dll (and agvn-mlang32.dll where Wine runs 32-bit programs) in Wine on a PC: probe.exe loads it as
# mlang.dll beside itself, as AgvnMlangFix puts it, with WINEDLLOVERRIDES=mlang=n,b as GameDllOverrides sets, and
# compares it with Wine's own mlang.dll. Pass: "ALL PASSED" for each. Needs wine (9.x), mingw-w64 and the DLLs
# (build-mlang.sh).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
assets="$here/../../../app/src/main/assets/agvn"
work="$(mktemp -d)"
wine=$(command -v wine || command -v wine64 || echo /usr/lib/wine/wine64)
export WINEPREFIX="$work/prefix" WINEDEBUG=-all WINEDLLOVERRIDES="mlang=n,b"
"$wine" wineboot -i >/dev/null 2>&1 || true
for b in 64 32; do
    gcc=x86_64; [ "$b" = 32 ] && gcc=i686
    mkdir -p "$work/$b"
    "$gcc-w64-mingw32-gcc" -O2 -o "$work/$b/probe.exe" "$here/probe.c" -lole32 -lgdi32
    cp "$assets/agvn-mlang$b.dll" "$work/$b/mlang.dll"
    echo "-- $b-bit"
    "$wine" "$work/$b/probe.exe" 2>&1 | tr -d '\r' || echo "(Wine did not run the $b-bit probe)"
done
"$(dirname "$wine")/wineserver" -k 2>/dev/null || true
