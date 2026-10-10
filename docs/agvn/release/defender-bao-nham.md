# Windows Defender báo APK (v0.1.0, 28/09/2026)

Quét `AGVN-Player-v0.1.0.apk` bằng `MpCmdRun -Scan -ScanType 3` (Windows 10, máy build):

| File trong APK | Nguồn | Xử lý |
|---|---|---|
| `assets/proton-9.0-arm64ec.tar.zst` | Proton 9.0 arm64ec của Ludashi, SHA-256 khớp `proton_arm64ec_sha256` trong `scripts/agvn/pins.txt` | Bắt buộc cho mọi game; gửi mẫu báo nhầm cho Microsoft |
| `assets/dxwrapper/dxvk-1.11.1-sarek.tzst` (d3d8/d3d9/d3d10core/d3d11/dxgi.dll) | DXVK bản cho GPU cũ, không phải mặc định | Đã bỏ khỏi app; cấu hình cũ tự chuyển sang DXVK 1.10.3 |
| `assets/agvn/proton-10.0-4-arm64ec.wcp` (từ v0.1.21, chưa quét) | Proton 10.0-4 arm64ec của The412Banner/proton-wine, SHA-256 khớp `proton10_wcp_sha256` | Cùng loại file với Proton 9 nên có thể bị báo giống vậy; xử lý như Proton 9 (quyết định bên dưới) |

Tên mối đe dọa: "Unknown" lần quét đầu; sau khi bỏ DXVK sarek chỉ còn Proton, báo `Trojan:Win32/Suschil!rfn` (nhận dạng heuristic).

## Gửi báo nhầm cho Microsoft (maintainer)
1. Mở https://www.microsoft.com/en-us/wdsi/filesubmission → chọn "Home customer", đăng nhập tài khoản Microsoft.
2. File: `C:\AGVN\ap\app\src\main\assets\proton-9.0-arm64ec.tar.zst` (73 MB).
3. "Incorrectly detected as malware/malicious", ghi chú: "Proton 9.0 (Wine) runtime for an open-source Android emulator
   (Winlator-Ludashi fork). Contains Windows system DLL replacements. Source: github.com/ValveSoftware/Proton".
4. Chờ kết quả (thường 1–3 ngày), cập nhật định nghĩa Defender (`MpCmdRun -SignatureUpdate`), quét lại APK.

## Quyết định của maintainer (28/09/2026)
Khách AGVN tải APK thẳng trên điện thoại Android, nơi Windows Defender không quét, nên v0.1.0 được phát hành dù Defender
trên Windows vẫn báo file Proton. Không gửi báo nhầm lúc này. Nếu sau này phát hành qua máy tính (Google Drive tải về PC,
link Windows...) thì gửi báo nhầm theo các bước trên trước.
