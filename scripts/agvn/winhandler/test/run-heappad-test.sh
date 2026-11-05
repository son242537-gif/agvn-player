#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Tests agvn-winhandler.exe /heappad (app/src/main/cpp/agvn/heap-pad.c) in Wine on a PC. heappad-probe.exe reads 40
# UTF-16 scripts with Japanese and Vietnamese text as SRPG Studio's game.exe does (size + 1 zeroed bytes, then
# ParseScriptText) and writes how many JScript refused. Pass: through the starter with /heappad, "0 errors" and "heap
# pad on"; without it, some errors on the line after a script's last (2 to 5 of 40 in Wine 9.0). Needs wine (9.x,
# wine64 and wine32), xvfb-run, mingw-w64 (i686 gcc) and python3.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
starter="$here/../../../../app/src/main/assets/agvn/agvn-winhandler.exe"
work="$(mktemp -d)"
mkdir "$work/js"
i686-w64-mingw32-gcc -municode -O2 -o "$work/js/probe32.exe" "$here/heappad-probe.c" -lole32 -loleaut32 -luuid \
    -ladvapi32
python3 -I - "$work/js" <<'PY'
import random, sys
random.seed(7)
jp = ['ユニットの移動処理', '戦闘画面の描画', 'ターン終了時の処理', 'アイテムを使用する', '経験値の計算']
vi = ['Đơn vị di chuyển', 'Vẽ màn chiến đấu', 'Kết thúc lượt', 'Dùng vật phẩm', 'Tính kinh nghiệm']
for f in range(40):
    lines = ['// ' + random.choice(jp) + '：ファイル ' + str(f), 'var Obj%d = {' % f]
    for i in range(random.randint(30, 700)):
        k = random.random()
        if k < 0.3: lines.append('    // ' + random.choice(jp) + ' ' + random.choice(jp))
        elif k < 0.6: lines.append('    t%d: "%s %s",' % (i, random.choice(vi), random.choice(jp)))
        else: lines.append('    f%d: function(a) { return a + %d; },' % (i, i))
    lines += ['    end: 0', '};']
    text = '\r\n'.join(lines) + '\r\n'
    open('%s/s%02d.js' % (sys.argv[1], f), 'wb').write(b'\xff\xfe' + text.encode('utf-16le'))
PY
export WINEPREFIX="$work/prefix" WINEDEBUG=-all
dos="Z:$(echo "$work/js" | tr / '\\')"
cd "$work/js"
run() {
    rm -f probe-out.txt
    timeout 120 xvfb-run -a wine "$starter" "$@" /dir "$dos" probe32.exe --file s*.js > starter.log 2>&1 || true
    wineserver -k 2>/dev/null || true
    echo "$(tr -d '\r' < probe-out.txt | tail -1) | $(grep -a 'heap pad' starter.log | tr '\n' ' ')"
}
timeout 300 xvfb-run -a wine wineboot -i >/dev/null 2>&1 || true
echo "-- with /heappad"; run /heappad
echo "-- without"; run
