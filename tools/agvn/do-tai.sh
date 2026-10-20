#!/usr/bin/env bash
# Đo tải trên điện thoại khi đang chơi game trong AGVN Player (qua adb, không cần root).
#   bash tools/agvn/do-tai.sh [so_mau=12] [giay_moi_mau=10] [file_csv=do-tai.csv]
# Mỗi mẫu ghi: thời gian, RAM trống (MemAvailable), PSS của app, nhiệt độ pin, trạng thái nhiệt Android,
# thời gian GPU bận của app (dumpsys gpu --gpuwork, nếu máy hỗ trợ). FPS đọc trên HUD trong game.
# Dùng Git Bash trên Windows hoặc terminal Linux/macOS; cần adb trong PATH và điện thoại đã bật gỡ lỗi USB.
# Copyright (c) 2026 agvn.io - MIT License.
set -u
N=${1:-12}; INTERVAL=${2:-10}; OUT=${3:-do-tai.csv}; PKG=com.agvn.player
adb get-state >/dev/null 2>&1 || { echo "Không thấy điện thoại qua adb"; exit 1; }
UID_APP=$(adb shell dumpsys package $PKG | tr -d '\r' | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1)
gpu_ns() { adb shell dumpsys gpu --gpuwork 2>/dev/null | tr -d '\r' | awk -v u="$UID_APP" '$0 ~ "uid: *"u"[^0-9]" || $0 ~ "uid="u"[^0-9]" {for(i=1;i<=NF;i++) if($i ~ /total_active_duration_ns/) {split($i,a,"="); s+=a[2]}} END{print s+0}'; }
echo "thoi_gian,ram_trong_mb,pss_app_mb,nhiet_pin_c,trang_thai_nhiet,gpu_ban_phan_tram" > "$OUT"
prev_gpu=$(gpu_ns); prev_t=$(date +%s%N)
for i in $(seq 1 "$N"); do
  sleep "$INTERVAL"
  avail=$(adb shell cat /proc/meminfo | tr -d '\r' | awk '/MemAvailable/ {printf "%d", $2/1024}')
  pss=$(adb shell dumpsys meminfo $PKG | tr -d '\r' | awk '/TOTAL PSS:/ {printf "%d", $3/1024; exit} /^ *TOTAL / {printf "%d", $2/1024; exit}')
  temp=$(adb shell dumpsys battery | tr -d '\r' | awk '/temperature/ {printf "%.1f", $2/10}')
  status=$(adb shell dumpsys thermalservice 2>/dev/null | tr -d '\r' | awk -F': ' '/Thermal Status/ {print $2; exit}')
  cur_gpu=$(gpu_ns); cur_t=$(date +%s%N)
  busy=$(awk -v a="$prev_gpu" -v b="$cur_gpu" -v t0="$prev_t" -v t1="$cur_t" 'BEGIN{ if (b>a && t1>t0) printf "%.1f", (b-a)*100/(t1-t0); else print "" }')
  prev_gpu=$cur_gpu; prev_t=$cur_t
  line="$(date +%H:%M:%S),${avail},${pss},${temp},${status},${busy}"
  echo "$line" | tee -a "$OUT"
done
echo "Đã ghi $OUT"
