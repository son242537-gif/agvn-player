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

## Một kênh

Mỗi bản là một release `v0.1.3`, `v0.1.4`… trên GitHub. App chỉ đọc release mới nhất ("Latest"), nên mọi máy nhận cùng
một bản. Không có bản thử nghiệm.

## Đăng bản mới (trên PC của anh Sơn)

**Chuẩn bị một lần:**

1. Cài GitHub CLI: `winget install GitHub.cli`.
2. Đăng nhập: `gh auth login`.
3. Có khoá ký ở `C:\AGVN\Keys\keystore.properties`. Có thể đặt đường dẫn khác bằng `-Keystore` hoặc biến
   `AGVN_KEYSTORE_PROPS`.

**Mỗi lần ra bản:**

1. Trước đó, phiên làm việc trên cloud chuẩn bị hai thứ trên nhánh:
   - tăng `AGVN_VERSION_CODE` và `AGVN_VERSION_NAME` trong `gradle.properties`;
   - viết ghi chú cho người chơi vào `docs/agvn/release/ghi-chu-phat-hanh-v<phiên bản>.txt`.
2. Trong `C:\AGVN\ap`, sau khi đã `git fetch` và checkout đúng nhánh (commit phải có trên GitHub), chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\agvn\dang-ban-cap-nhat.ps1

# Chỉ dựng và tạo file, không đăng
powershell -ExecutionPolicy Bypass -File .\tools\agvn\dang-ban-cap-nhat.ps1 -DryRun
```

Windows mặc định không cho chạy file `.ps1` ("running scripts is disabled on this system"). `-ExecutionPolicy Bypass`
chỉ cho phép trong lần chạy đó, không đổi cài đặt của máy.

Script làm những việc sau:

1. Đọc phiên bản trong `gradle.properties`. Mã phải lớn hơn bản đang đăng, vì Android chỉ cài đè bản có mã lớn hơn.
   Nếu chưa tăng, script dừng và báo.
2. Chạy bài kiểm tra và dựng APK ký bằng khoá phát hành (`-PagvnRequireReleaseSigning=true`).
3. Tạo `build\agvn-update\AGVN-Player.apk` và `agvn-update.txt` (mã, tên, đường tải, dung lượng,
   SHA-256, ghi chú).
4. Đăng release `v<phiên bản>`. Release được tạo ở dạng nháp, tải đủ hai file lên rồi mới công bố. Nhờ vậy điện
   thoại không bao giờ thấy thông tin trỏ tới một APK chưa có.

Muốn cài bằng cáp thì dùng đúng APK trong `build\agvn-update`. Bản dựng tay bằng `gradlew` cũng dùng mã trong
`gradle.properties`, nhưng chỉ ký khoá phát hành khi tìm thấy `keystore.properties`. Không có thì nó ký khoá debug,
và Android báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` khi cài đè. Chạy một lần
`setx AGVN_KEYSTORE_PROPS "C:\AGVN\Keys\keystore.properties"` rồi mở cửa sổ PowerShell mới để bản dựng tay cũng ký đúng khoá.

## Link tải cho người chơi

Link tải chính thức, chốt từ 01/10/2026 và không đổi nữa. Bấm là tải ngay bản mới nhất, không mở trang GitHub:

https://github.com/son242537-gif/agvn-player/releases/latest/download/AGVN-Player.apk

Link chạy được vì mọi release đều có file tên đúng `AGVN-Player.apk` (script tự đặt). Không bao giờ đổi tên file này.

- Dùng làm nút "Tải AGVN Player" trên agvn.io.vn, hoặc cho một địa chỉ ngắn như `agvn.io.vn/tai` chuyển hướng về đây.
  Ra bản mới không phải đổi link.
- Chrome hỏi "Tệp này có thể gây hại": chọn "Vẫn tải xuống". Mọi file APK tải từ web đều bị hỏi như vậy.
- Mở link trong Facebook, Messenger hay Zalo mà không tải: bấm ⋮ → "Mở bằng trình duyệt", rồi bấm lại link.

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
- **"Chưa có bản cập nhật nào được đăng":** chưa có release nào có `agvn-update.txt`.
- **HyperOS/MIUI** có thể hiện thêm màn hình quét an toàn của Xiaomi trước khi cài. Chỉ cần bấm tiếp.
