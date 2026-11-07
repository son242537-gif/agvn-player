#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Builds app/src/main/assets/agvn/agvn-mlang64.dll and agvn-mlang32.dll from app/src/main/cpp/agvn/mlang-fontlink.c:
# Wine's mlang.dll with the font linking of GDI+ fixed, for a .NET game that ends with 0xC0000005 at Vietnamese text
# (AgvnMlangFix puts it beside the game's exe as mlang.dll). Test it in Wine with scripts/agvn/mlang/run-test.sh.
#
# Needs mingw-w64 from Ubuntu: apt-get install gcc-mingw-w64-x86-64-win32 gcc-mingw-w64-i686-win32. No timestamp in
# the DLLs, so the same compiler gives the same files.
set -euo pipefail
cd "$(dirname "$0")/../../.."
src=app/src/main/cpp/agvn/mlang-fontlink
for arch in x86_64:64 i686:32; do
    out=app/src/main/assets/agvn/agvn-mlang${arch#*:}.dll
    "${arch%%:*}-w64-mingw32-gcc" -O2 -s -shared -static-libgcc -Wl,--no-insert-timestamp -Wl,--enable-stdcall-fixup \
        -o "$out" "$src.c" "$src.def" -lole32
    sha256sum "$out"
done
x86_64-w64-mingw32-gcc --version | head -1
