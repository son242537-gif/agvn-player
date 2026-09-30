# Cập nhật ứng dụng ngay trong app

Điện thoại tự tải bản AGVN Player mới từ **GitHub Releases** của kho `son242537-gif/agvn-player`. Người chơi chỉ
cần bấm "Cập nhật", rồi bấm "Cập nhật" thêm một lần trên hộp thoại của Android. Không cần máy tính, cáp hay adb.

## Người chơi thấy gì

- **Tự kiểm tra:**
  - Mỗi ngày một lần, khi mở app, app xem có bản mới không.
  - Nếu có, app hiện "Có bản mới x.y.z" kèm ghi chú và dung lượng. Người chơi chọn "Cập nhật" hoặc "Để sau".
  - Tắt được trong Cài đặt → Cập nhật ứng dụng.
- **Kiểm tra bằng tay:** Cài đặt → **Cập nhật ứng dụng** → **Kiểm tra cập nhật**.
- **Lần đầu cập nhật:** Android hỏi quyền "Cài ứng dụng không rõ nguồn gốc" cho AGVN Player (chỉ một lần). App mở
  sẵn đúng màn hình đó. Bật xong, quay lại bấm "Cài tiếp".
- **Mất mạng giữa chừng hay bấm Hủy:** phần đã tải được giữ lại, lần sau tải tiếp.
- **Sau khi cập nhật:** game, save và cài đặt vẫn giữ nguyên, vì đây là cài đè cùng chữ ký, không phải gỡ ra cài lại.
- **Không cài được không cần hỏi:** app giữ mức Android 9 (targetSdk 28) như Winlator, nên Android luôn bắt bấm
  xác nhận.

## Hai kênh

| Kênh | Nơi để trên GitHub | Ai nhận |
|---|---|---|
| Chính thức | release `v0.1.3`, `v0.1.4`… (bản "Latest") | mọi máy |
| Thử nghiệm | một pre-release tên `beta`, mỗi bản dựng thử thay file cũ | máy bật "Nhận bản thử nghiệm" |

Máy bật "Nhận bản thử nghiệm" nhận bản mới nhất của cả hai kênh.

## Đăng bản mới (trên PC của anh Sơn)

**Chuẩn bị một lần:**

1. Cài GitHub CLI: `winget install GitHub.cli`.
2. Đăng nhập: `gh auth login`.
3. Có khoá ký ở `C:\AGVN\Keys\keystore.properties`. Có thể đặt đường dẫn khác bằng `-Keystore` hoặc biến
   `AGVN_KEYSTORE_PROPS`.

**Mỗi lần ra bản:** trong `C:\AGVN\ap`, sau khi đã `git fetch` và checkout đúng nhánh (commit phải có trên GitHub):

```powershell
# Bản thử nghiệm: vào release "beta"
.\tools\agvn\dang-ban-cap-nhat.ps1 -Notes "Sửa nút ? và chuột phải"

# Bản chính thức: tạo release v0.1.3 cho mọi máy
.\tools\agvn\dang-ban-cap-nhat.ps1 -Stable -Version 0.1.3 -NotesFile docs\agvn\release\ghi-chu-phat-hanh-v0.1.3.txt

# Chỉ dựng và tạo file, không đăng
.\tools\agvn\dang-ban-cap-nhat.ps1 -DryRun
```

Script làm những việc sau:

1. Lấy **mã phiên bản** lớn hơn mọi bản đã đăng, ở cả hai kênh, và lớn hơn `gradle.properties`. Android chỉ cài
   đè bản có mã lớn hơn.
   - Bản thử có tên dạng `0.1.2-beta.7`.
   - Bản chính thức: sau khi đăng, ghi mã và tên mới vào `gradle.properties` rồi commit (script sẽ nhắc).
2. Chạy bài kiểm tra và dựng APK ký bằng khoá phát hành (`-PagvnRequireReleaseSigning=true`).
3. Tạo `build\agvn-update\AGVN-Player-<tên>.apk` và `agvn-update.txt` (mã, tên, đường tải, dung lượng, SHA-256,
   ghi chú).
4. Đăng lên GitHub.
   - Với `beta`, APK được tải lên trước, rồi mới tới `agvn-update.txt`, sau cùng xoá APK thử cũ.
   - Nhờ thứ tự đó, điện thoại không bao giờ thấy thông tin trỏ tới một APK chưa có.

## An toàn

Trước khi hỏi Android cài, app kiểm tra:

- `agvn-update.txt` chỉ được trỏ tới APK nằm trong release của chính kho này, qua https;
- APK tải về đúng dung lượng và SHA-256 ghi trong `agvn-update.txt`;
- APK đúng là AGVN Player (`com.agvn.player`), đúng mã phiên bản, và mới hơn bản đang cài;
- APK ký cùng khoá với app đang cài. Android cũng tự từ chối bản khác khoá; app kiểm trước để báo lỗi dễ hiểu hơn.

Khoá ký chỉ nằm trên PC của anh Sơn. Kẻ xấu có sửa được file trên GitHub cũng không ký được APK hợp lệ, nên điện
thoại không cài.

App không gửi gì về máy điện thoại, chỉ tải hai file công khai. Đây là ngoại lệ đã được anh Sơn đồng ý, ghi ở quy
định 3 trong `CLAUDE.md`.

## Mã nguồn

- `app/src/main/java/com/winlator/cmod/agvn/`:
  - `AgvnUpdateInfo`: đọc `agvn-update.txt`;
  - `AgvnUpdater`: tải, tải tiếp, kiểm tra, dọn file;
  - `AgvnUpdateDialogs`: mục Cài đặt và kiểm tra mỗi ngày;
  - `AgvnUpdateInstall`: tải có thanh tiến độ và mở trình cài của Android.
- Quyền `REQUEST_INSTALL_PACKAGES` và `cache-path` `updates/` trong `res/xml/file_paths.xml` (FileProvider có sẵn).
- `tools/agvn/dang-ban-cap-nhat.ps1`: script đăng bản.

## Khi có lỗi

- **"không cùng chữ ký":** máy đang cài một bản dựng ký khoá khác, ví dụ bản debug. Cài bản ký khoá phát hành bằng
  adb một lần, từ đó cập nhật trong app được.
- **"Chưa có bản cập nhật nào được đăng":** chưa có release chính thức nào có `agvn-update.txt`. Bản thử chỉ hiện khi
  bật "Nhận bản thử nghiệm".
- **HyperOS/MIUI** có thể hiện thêm màn hình quét an toàn của Xiaomi trước khi cài. Chỉ cần bấm tiếp.
