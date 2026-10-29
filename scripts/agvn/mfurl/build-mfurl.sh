#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Builds app/src/main/assets/agvn/agvn-mfurl64.dll and agvn-mfurl32.dll from app/src/main/cpp/agvn/mf-file-url.c:
# Media Foundation's "file:" URL handler that opens "file:///X:/..." as Windows does (AgvnMfFileUrl puts it in a game's
# container). Test it in Wine with scripts/agvn/mfurl/run-test.sh.
#
# Needs mingw-w64 from Ubuntu: apt-get install gcc-mingw-w64-x86-64-win32 gcc-mingw-w64-i686-win32. No timestamp in
# the DLLs, so the same compiler gives the same files.
set -euo pipefail
cd "$(dirname "$0")/../../.."
src=app/src/main/cpp/agvn/mf-file-url
for arch in x86_64:64 i686:32; do
    out=app/src/main/assets/agvn/agvn-mfurl${arch#*:}.dll
    "${arch%%:*}-w64-mingw32-gcc" -O2 -s -shared -static-libgcc -Wl,--no-insert-timestamp -Wl,--enable-stdcall-fixup \
        -o "$out" "$src.c" "$src.def" -lshlwapi -lole32 -luuid
    sha256sum "$out"
done
x86_64-w64-mingw32-gcc --version | head -1
