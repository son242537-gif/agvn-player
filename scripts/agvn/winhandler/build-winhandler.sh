#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Builds app/src/main/assets/agvn/agvn-winhandler.exe from app/src/main/cpp/winlator/winhandler.c: Winlator's helper
# that starts a game in Wine and takes the app's mouse, keyboard and process requests, with AGVN's RC_AGVN_POINTER
# (a touch as a real mouse, raw input included) and its command line read as Unicode, and from AGVN 2 (0.1.71) its
# RC_AGVN_STATE (the game holds the cursor, a text caret shows) and finger moves as relative mouse moves, and from AGVN 3
# (0.1.74) RC_AGVN_GAME_ENDED: it ends, and Wine with it, once the game and its processes have ended, and from AGVN 4
# (0.1.81) /heappad (app/src/main/cpp/agvn/heap-pad.c): a 32-bit game's zeroed heap blocks get two more zero bytes,
# for SRPG Studio's scripts, and from AGVN 5 (0.1.88) RC_AGVN_STATE's AGVN_STATE_TEXT_INPUT: the game turned on its
# own text input (an input context for its window, after it had none: SDL and Ren'Py, Unity, Unreal, Godot). Every game
# starts through it (AgvnStarter); the container's winhandler.exe only when it cannot be written there. Test it in Wine
# with scripts/agvn/winhandler/test/run-test.sh, /heappad with run-heappad-test.sh, and the text input with
# run-typing-test.sh (same folder).
#
# Needs mingw-w64 from Ubuntu: apt-get install gcc-mingw-w64-x86-64-win32 binutils-mingw-w64-i686. No timestamp in
# the exe, so the same compiler gives the same file. The source includes psapi.h before windows.h, hence the two
# -include.
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=app/src/main/assets/agvn/agvn-winhandler.exe
# /heappad's 32-bit code, as bytes for heap-pad.c
stub="$(mktemp -d)"
i686-w64-mingw32-as -o "$stub/stub.o" app/src/main/cpp/agvn/heap-pad-stub.s
i686-w64-mingw32-objcopy -O binary -j .text "$stub/stub.o" "$stub/stub.bin"
{ echo "static const unsigned char heap_pad_stub_bin[] = {"
  od -An -v -tx1 "$stub/stub.bin" | sed 's/ *\([0-9a-f][0-9a-f]\)/0x\1, /g; s/^/    /'
  echo "};"; } > "$stub/heap-pad-stub.h"
x86_64-w64-mingw32-gcc -O2 -s -mwindows -DPSAPI_VERSION=2 -include winsock2.h -include windows.h -I"$stub" \
    -Wl,--no-insert-timestamp -o "$out" app/src/main/cpp/winlator/winhandler.c app/src/main/cpp/agvn/heap-pad.c \
    -lws2_32 -lntdll
rm -r "$stub"
x86_64-w64-mingw32-gcc --version | head -1
sha256sum "$out"
