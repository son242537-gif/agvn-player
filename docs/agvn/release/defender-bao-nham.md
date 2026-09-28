# Windows Defender báo APK (v0.1.0, 28/09/2026)

Quét `AGVN-Player-v0.1.0.apk` bằng `MpCmdRun -Scan -ScanType 3` (Windows 10, máy build):

| File trong APK | Nguồn | Xử lý |
|---|---|---|
| `assets/proton-9.0-arm64ec.tar.zst` | Proton 9.0 arm64ec của Ludashi, SHA-256 khớp `proton_arm64ec_sha256` trong `scripts/agvn/pins.txt` | Bắt buộc cho mọi game; gửi mẫu báo nhầm cho Microsoft |
| `assets/dxwrapper/dxvk-1.11.1-sarek.tzst` (d3d8/d3d9/d3d10core/d3d11/dxgi.dll) | DXVK bản cho GPU cũ, không phải mặc định | Đã bỏ khỏi app; cấu hình cũ tự chuyển sang DXVK 1.10.3 |

Tên mối đe dọa: "Unknown" (Defender không nêu tên họ mã độc).

## Gửi báo nhầm cho Microsoft (maintainer)
1. Mở https://www.microsoft.com/en-us/wdsi/filesubmission → chọn "Home customer", đăng nhập tài khoản Microsoft.
2. File: `C:\AGVN\ap\app\src\main\assets\proton-9.0-arm64ec.tar.zst` (73 MB).
3. "Incorrectly detected as malware/malicious", ghi chú: "Proton 9.0 (Wine) runtime for an open-source Android emulator
   (Winlator-Ludashi fork). Contains Windows system DLL replacements. Source: github.com/ValveSoftware/Proton".
4. Chờ kết quả (thường 1–3 ngày), cập nhật định nghĩa Defender (`MpCmdRun -SignatureUpdate`), quét lại APK.

Chưa phát hành APK cho khách khi Defender còn báo.
