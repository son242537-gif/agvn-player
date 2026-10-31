#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Builds app/src/main/assets/agvn/agvn-winhandler.exe from app/src/main/cpp/winlator/winhandler.c: Winlator's helper
# that starts a game in Wine and takes the app's mouse, keyboard and process requests, with AGVN's RC_AGVN_POINTER
# (a touch as a real mouse, raw input included) and its command line read as Unicode, and from AGVN 2 (0.1.71) its
# RC_AGVN_STATE (the game holds the cursor, a text caret shows) and finger moves as relative mouse moves, and from AGVN 3
# (0.1.74) RC_AGVN_GAME_ENDED: it ends, and Wine with it, once the game and its processes have ended. Every game
# starts through it (AgvnStarter); the container's winhandler.exe only when it cannot be written there. Test it in
# Wine with scripts/agvn/winhandler/test/run-test.sh.
#
# Needs mingw-w64 from Ubuntu: apt-get install gcc-mingw-w64-x86-64-win32. No timestamp in the exe, so the same
# compiler gives the same file. The source includes psapi.h before windows.h, hence the two -include.
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=app/src/main/assets/agvn/agvn-winhandler.exe
x86_64-w64-mingw32-gcc -O2 -s -mwindows -DPSAPI_VERSION=2 -include winsock2.h -include windows.h \
    -Wl,--no-insert-timestamp -o "$out" app/src/main/cpp/winlator/winhandler.c -lws2_32
x86_64-w64-mingw32-gcc --version | head -1
sha256sum "$out"
