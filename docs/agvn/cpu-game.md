# CPU của game: nhật ký đứng hình và ưu tiên nhân mạnh

Game Windows chạy qua FEX hoặc Box64, nên thread chính của game thường là thread bận nhất, và game nặng thường bị
CPU giới hạn. Đo ngày 01/10 trên POCO F8 Pro: một game Unity chạy 35–48 FPS (giới hạn 60). Trong cả 5 lần đứng hình,
thread chính dùng khoảng 95% một nhân, nhưng chỉ chạy trên cpu0–5 (khi đó bị khoá ở 2745 MHz). Nó không lần nào lên
cpu6–7, hai nhân nhanh nhất.

## Dòng "Đứng hình" trong nhật ký

Mỗi lần game ngừng gửi khung hình từ 1 giây trở lên, nhật ký của lần chơi có một dòng "Đứng hình …". Logcat có dòng
tương ứng, thẻ `AGVN`, bắt đầu bằng `frame stall`. Dòng này ghi:

- giới hạn FPS đang chạy bằng gì (không giới hạn, bộ hẹn giờ, hay khớp nhịp màn hình), và với khớp nhịp thì app có
  giữ bộ đệm nào của game không (`AgvnFrameStalls`);
- CPU của game, đo từ giây thứ 0,5 của lần đứng: thread bận nhất (tên, số thread, % một nhân, nhân chạy gần nhất)
  và cả game. "Thread chính" là thread đầu tiên của tiến trình game (số thread bằng số tiến trình). Dưới Wine và FEX,
  thread này chạy vòng lặp của game.
  - Thread bận nhất từ 50% một nhân trở lên: **game đang bận** (đang tải, hoặc đang tính).
  - Dưới 10%: **game đang chờ**, không tính gì. Nếu khi đó app không giữ bộ đệm nào thì game đang chờ thứ khác.

Tiến trình của game là tiến trình sở hữu cửa sổ game (`_NET_WM_PID`). Nếu số đó không phải tiến trình của app, app
lấy tiến trình của app dùng nhiều CPU nhất trong 2 giây gần nhất, trừ chính app và `wineserver` (`AgvnGameThreads`).

## "Ưu tiên nhân CPU mạnh (thử nghiệm)"

Ô này nằm trong menu game, dưới "Giới hạn FPS". Ô mặc định tắt và lưu riêng cho từng game (`agvnCpuBoost`).

Khi bật, app dùng gợi ý hiệu năng của Android (ADPF, `PerformanceHintManager`, Android 12 trở lên):

- Cứ 2 giây, app chọn tối đa 3 thread: luôn có thread chính (nếu nó có chạy), thêm các thread bận nhất từ 30% một
  nhân trở lên. Danh sách xếp theo số thread, nên cùng các thread đó thì app không gửi lại cho Android.
- Thời gian mục tiêu cho mỗi khung là 1/giới hạn FPS. Không đặt giới hạn thì lấy 1/60 giây.
- Cứ 100 ms, app báo thời gian CPU mà thread bận nhất đã dùng cho mỗi khung. Trong lúc đứng hình, app báo phần đã dùng
  từ đầu lần đứng.
- App báo thời gian CPU, không báo khoảng cách giữa hai khung. Một visual novel chỉ gửi một khung mỗi giây là game
  đang rảnh, không phải game chậm, nên không bị đẩy xung.

Khi khung hình trễ, Android có thể chuyển các thread đó sang nhân nhanh hơn hoặc tăng xung. Khi khung kịp, Android
có thể hạ xung.

Đo ngày 01/10 trên POCO F8 Pro: Android nhận phiên gợi ý (`dumpsys performance_hint` có phiên với thread của tiến
trình game), nhưng thread chính vẫn chạy trên cpu4–5. Vì vậy khi ô này bật và thread chính dùng từ 30% một nhân, app
còn **ghim thread chính vào cụm nhân có xung tối đa cao nhất** (`AgvnMainThreadPin`, bằng `sched_setaffinity`):

- Cụm nhân lấy từ `cpufreq/policy*/cpuinfo_max_freq`. Máy chỉ có một loại nhân thì app không ghim.
- Chỉ ghim vào những nhân thread đó vốn được chạy, nên danh sách CPU của game vẫn có hiệu lực.
- Wine đặt lại danh sách CPU, hoặc nhân bị tắt tạm làm thread bị dời đi: lần kiểm tra sau (2 giây) app ghim lại.
- Tắt ô hoặc thoát game: thread chính trở về danh sách CPU cũ.
- Logcat ghi `game main thread <tid> pinned to cpus 6-7 (was 0-7)` và `back on cpus …`.

Android chỉ nhận gợi ý cho thread chạy dưới user của app. Tiến trình game là tiến trình con của app nên chạy cùng
user. Máy không hỗ trợ thì logcat ghi `performance hints: this phone does not support them`. Máy từ chối 3 lần liên
tiếp thì app thôi gửi gợi ý, và logcat ghi lý do (`AgvnCpuBoost`).

Kiểm tra trên máy:

```text
adb shell dumpsys performance_hint                       # phiên đang mở, kèm danh sách tid
adb shell ps -T -p <pid game> -o tid,psr,pcpu,comm      # psr: nhân đang chạy mỗi thread
adb logcat -s AGVN                                       # "performance hints: game threads [...]"
```

## FEX theo từng game

Biến `FEX_TSOENABLED` và `FEX_HALFBARRIERTSOENABLED` giả lập thứ tự bộ nhớ của x86. Chế độ này tốn nhiều CPU nhất.
Biến này đặt riêng được cho từng game:

- **Cài đặt của game → "FEXCore Preset"**. Preset "Hiệu năng" tắt toàn bộ TSO; "Trung gian" (mặc định) vẫn bật TSO.
- Muốn chỉ tắt TSO thì tạo một preset tùy chỉnh trong Cài đặt của app, phần FEXCore, rồi chọn preset đó cho game.

Biến môi trường của game hoặc môi trường chạy được áp sau preset, nên sẽ đè preset. Game đa luồng (Unity) có thể văng
khi tắt TSO, nên phải chơi thử.
