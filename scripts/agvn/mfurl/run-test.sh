#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-mfurl64.dll and agvn-mfurl32.dll in Wine on a PC, with the registry entries AgvnMfFileUrl writes.
# probe.exe opens URLs through Media Foundation's source resolver, first with Wine's own "file:" handler, then with
# AGVN's. Pass: every line of the second run (64-bit and 32-bit) ends in 0x00000000; the first run shows Wine's bug
# ("file:///C:/..." -> 0x80070003). Needs wine (9.x, with wine32), mingw-w64 and the DLLs (build-mfurl.sh).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
assets="$here/../../../app/src/main/assets/agvn"
work="$(mktemp -d)"
x86_64-w64-mingw32-gcc -municode -O2 -o "$work/probe64.exe" "$here/probe.c" -lmfplat -lmfuuid -lole32
i686-w64-mingw32-gcc -municode -O2 -o "$work/probe32.exe" "$here/probe.c" -lmfplat -lmfuuid -lole32
export WINEPREFIX="$work/prefix" WINEDEBUG=-all
wine wineboot -i >/dev/null 2>&1
c="$WINEPREFIX/drive_c"
echo movie > "$c/a-0.mp4"
echo movie > "$c/a b.mp4"
urls=('file:///C:/a-0.mp4' 'file:///c|/a-0.mp4' 'file:///C:/a%20b.mp4' 'file://C:/a-0.mp4' 'C:\a-0.mp4' 'file:///C:/none.mp4')
run() { for b in 64 32; do echo "-- probe$b"; wine "$work/probe$b.exe" "${urls[@]}" | tr -d '\r'; done; }
echo "== Wine's handler"
run
cp "$assets/agvn-mfurl64.dll" "$c/windows/system32/agvn-mfurl.dll"
cp "$assets/agvn-mfurl32.dll" "$c/windows/syswow64/agvn-mfurl.dll"
clsid='{D241A047-AB6F-43EF-8C31-78A96B9D1EA2}'
wine reg add 'HKCU\Software\Microsoft\Windows Media Foundation\SchemeHandlers\file:' /v "$clsid" /d 'AGVN' /f >/dev/null
for key in 'HKLM\Software\Classes\CLSID' 'HKLM\Software\Classes\Wow6432Node\CLSID'; do
    wine reg add "$key\\$clsid\\InprocServer32" /reg:64 /ve /d 'C:\windows\system32\agvn-mfurl.dll' /f >/dev/null
    wine reg add "$key\\$clsid\\InprocServer32" /reg:64 /v ThreadingModel /d Both /f >/dev/null
done
echo "== AGVN's handler (file:///C:/none.mp4 stays 0x80070002: no such file)"
run
wineserver -w || true
grep -i -n -A3 "d241a047" "$WINEPREFIX/system.reg" "$WINEPREFIX/user.reg" | head -30
