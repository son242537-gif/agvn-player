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
- CPU của game, đo từ giây thứ 0,5 của lần đứng: thread bận nhất (tên, % một nhân, nhân đang chạy) và cả game.
  - Thread bận nhất từ 50% một nhân trở lên: **game đang bận** (đang tải, hoặc đang tính).
  - Dưới 10%: **game đang chờ**, không tính gì. Nếu khi đó app không giữ bộ đệm nào thì game đang chờ thứ khác.

Tiến trình của game là tiến trình sở hữu cửa sổ game (`_NET_WM_PID`). Nếu số đó không phải tiến trình của app, app
lấy tiến trình của app dùng nhiều CPU nhất trong 2 giây gần nhất, trừ chính app và `wineserver` (`AgvnGameThreads`).

## "Ưu tiên nhân CPU mạnh (thử nghiệm)"

Ô này nằm trong menu game, dưới "Giới hạn FPS". Ô mặc định tắt và lưu riêng cho từng game (`agvnCpuBoost`).

Khi bật, app dùng gợi ý hiệu năng của Android (ADPF, `PerformanceHintManager`, Android 12 trở lên):

- Cứ 2 giây, app chọn tối đa 3 thread bận nhất của game, mỗi thread từ 30% một nhân trở lên.
- Thời gian mục tiêu cho mỗi khung là 1/giới hạn FPS. Không đặt giới hạn thì lấy 1/60 giây.
- Cứ 100 ms, app báo thời gian CPU mà thread bận nhất đã dùng cho mỗi khung. Trong lúc đứng hình, app báo phần đã dùng
  từ đầu lần đứng.
- App báo thời gian CPU, không báo khoảng cách giữa hai khung. Một visual novel chỉ gửi một khung mỗi giây là game
  đang rảnh, không phải game chậm, nên không bị đẩy xung.

Khi khung hình trễ, Android có thể chuyển các thread đó sang nhân nhanh hơn hoặc tăng xung. Khi khung kịp, Android
có thể hạ xung.

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
