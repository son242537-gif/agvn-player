# Bàn giao sang PC (30/09/2026)

Tài liệu này dành cho phiên Claude Code chạy **trên PC của anh Sơn** (`C:\AGVN\ap`, Windows, có Android Studio + NDK 29,
khoá ký `C:\AGVN\Keys\keystore.properties`, điện thoại POCO F8 Pro cắm cáp USB). Phiên cloud đã dừng, mọi thứ đã
nằm trên GitHub. `CLAUDE.md` vẫn áp dụng: nhánh `agvn/pNN-<tên>`, PR vào `agvn/main` có "Device Test Checklist",
không commit khoá ký. Trên PC không có công cụ GitHub MCP: dùng `git` và `gh` (chạy `gh auth login` một lần) hoặc
web GitHub để mở và merge PR.

## Trạng thái

- **Cập nhật 02/10/2026:** `agvn/main` đã merge PR #25–#36, gồm **PR #34 (Zink Mesa 25.1.9, `zink@3`)**: game
  OpenGL hết văng trên POCO, RAM GPU giảm khoảng 0,3 GB. `agvn/main` vẫn ở 0.1.2 (mã 4).
- App đã phát hành **v0.1.4 (mã 6)** và **v0.1.5 (mã 7)** từ nhánh `agvn/p43-release-0.1.3` (gộp các nhánh
  `agvn/p28`…`p48`), chưa merge vào `agvn/main`. Các PR #37, #38, #40–#51 vẫn ở trạng thái nháp dù đã có trong
  bản phát hành. Bảng tiến độ đầy đủ: mục "Progress after the 10 phases" trong `ROADMAP.md`.
- Ren'Py và giấy phép GPL đã làm lại trên nhánh `agvn/p49-gpl-license` → `p50-renpy8` → `p53-game-session` (chưa có PR;
  đổi giấy phép cần anh Sơn quyết). ONScripter / mkxp-z vẫn tạm dừng.

## Việc làm tiếp (theo thứ tự)

### 1. Game OpenGL: chớp đen (Zink) — phần văng đã sửa ở #34

Triệu chứng trên POCO:
- ở cấu hình cao, game văng với `zink_context.c:700 assertion "…range <= …maxUniformBufferRange" failed`, rồi app
  thoát theo;
- ở cấu hình thấp, game vào được nhưng **màn hình chớp đen đều mỗi giây**.

Game này chạy tốt trên GameHub (Mesa 25.1.4). Nguyên nhân văng đã rõ: file Zink của Ludashi là Mesa 24.3.0 dựng ở chế
độ gỡ lỗi. Nguyên nhân chớp đen thì chưa rõ.

1. **Thử nhanh cho lỗi chớp** (không cần dựng lại gì): Cài đặt → tắt **"Dùng tiện ích DRI3"** → mở lại game ở cấu
   hình thấp. Nếu hết chớp thì lỗi nằm ở khâu đưa hình lên màn hình (DRI3/Present), không phải ở game. Đọc log theo
   mục "Đọc log" bên dưới, tìm dòng `zink`, `MESA`, `wrapper`, `present`.
2. **Zink mới đã merge (#34)** và có trong mọi bản từ 0.1.4. Nếu bản đang cài vẫn chớp, làm bước 3.

   Chỉ khi cần dựng lại (xem `scripts/agvn/zink/README.md`): script chỉ chạy trên Linux, nên trên Windows dùng
   **WSL2 Ubuntu 24.04**. Chạy mã dựng của Mesa là bước anh Sơn phải đồng ý trước.
   ```bash
   # trong WSL
   git clone https://github.com/son242537-gif/agvn-player ~/ap && cd ~/ap && git checkout agvn/main
   sudo bash scripts/agvn/cloud-setup.sh          # SDK + NDK 29 bản Linux vào /opt/android-sdk
   sudo apt-get install -y meson ninja-build pkgconf patch bison flex zstd binutils python3-mako python3-yaml python3-packaging
   cp /mnt/c/AGVN/ap/app/src/main/assets/imagefs.tar.zst app/src/main/assets/   # imagefs đã tải sẵn ở Windows
   ANDROID_HOME=/opt/android-sdk bash scripts/agvn/zink/build-zink.sh
   ```
   Script tự kiểm SHA của mọi nguồn và tự kiểm file kết quả trước khi thay
   `app/src/main/assets/graphics_driver/opengl_zink.tzst`.
3. Nếu Zink mới vẫn chớp: so env của app với GameHub (xem `ZINK_DEBUG`, `MESA_VK_WSI_*`, `WRAPPER_*` trong
   `XServerDisplayActivity.applyOpenGLDriverEnvVars` và `extractGraphicsDriverFiles`). Thử lần lượt
   `ZINK_DEBUG=compact,flushsync` và `MESA_VK_WSI_PRESENT_MODE=fifo` trong mục biến môi trường của game.

### 2. Chip Dimensity (Mali/Immortalis)

Kết quả rà soát: app chạy được trên Dimensity (driver của máy qua "wrapper"), nhưng chưa ai đo trên máy thật. Những
việc làm được ngay, không cần máy Dimensity:
- đưa thông tin GPU/Vulkan vào file "Xuất nhật ký lỗi" (`DiagnosticsExporter`);
- trên máy không phải Adreno, ẩn Turnip/Freedreno và lưu lại lựa chọn driver dự phòng (`DriverSafety`,
  `RuntimeSettingsSupport.kt`, `XServerDisplayActivity`: câu báo hiện lặp lại mỗi lần mở game);
- cảnh báo tiếng Việt khi chọn DXVK 2.x hoặc VKD3D trên Mali/PowerVR.

Cần máy Dimensity thật để kiểm (ví dụ Dimensity 8300: POCO X6 Pro, Xiaomi 14T): DX10/11 có mở được không, sai màu
BGRA/RGBA, mức xếp hạng máy.

## Dựng và cài APK (PowerShell, trong `C:\AGVN\ap`)

```powershell
git checkout agvn/main; git pull
$env:AGVN_KEYSTORE_PROPS = "C:\AGVN\Keys\keystore.properties"
.\gradlew.bat testReleaseUnitTest assembleRelease --no-daemon "-PagvnRequireReleaseSigning=true"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

## Đọc log của app (điện thoại cắm USB, đã bật Gỡ lỗi USB)

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb logcat -c                                   # xoá log cũ, rồi mở game và làm lại lỗi
& $adb logcat -d -v time > log-toan-bo.txt         # toàn bộ log sau khi tái hiện lỗi
$p = & $adb shell pidof -s com.agvn.player; & $adb logcat -d -v time --pid=$p > log-app.txt
Select-String -Path log-toan-bo.txt -Pattern "AGVN|XServerDisplayActivity|GuestProgramLauncher|GuestLauncher|GraphicsDriverExtraction|zink|MESA|wrapper|F/libc|F/DEBUG"
```

- **Nhãn log của app:**
  - `AGVN`: phần của AGVN (driver an toàn, chống nóng/RAM, sửa phím…);
  - `XServerDisplayActivity`, `GuestProgramLauncherComponent` và `GuestLauncher`: khởi chạy game, Wine, Box64;
  - `GraphicsDriverExtraction`: cài driver đồ hoạ.
- **Game văng ở mã máy:** tìm `F/libc` (`Fatal signal`, `abort`) và `F/DEBUG` (bản ghi crash). Lỗi Zink ở trên hiện
  đúng như vậy.
- **Log Wine/game:** Cài đặt → bật **"Bật debug Wine"** và **"Bật nhật ký ứng dụng"**, mở game, rồi
  `& $adb pull /sdcard/AGVN-Player/logs`. Tên file theo dạng `<tên exe>_<ngày giờ>.txt` và
  `<tên exe>_winlator_<ngày giờ>.txt`.
- **Gói gửi hỗ trợ:** Cài đặt → **"Xuất nhật ký lỗi"** tạo `/sdcard/Download/AGVN-nhat-ky-<ngày giờ>.zip` (logcat,
  thông tin máy, cài đặt). Lấy về bằng `& $adb pull /sdcard/Download/`.
- **RAM và nhiệt:**
  - `& $adb shell dumpsys meminfo com.agvn.player`
  - `& $adb shell dumpsys thermalservice`
  - `& $adb shell dumpsys battery`

## Câu mở đầu cho phiên Claude Code trên PC

> Đọc `docs/agvn/handoff-pc.md` và `CLAUDE.md`. Điện thoại POCO đang cắm USB. Làm tiếp mục 1: tự chạy adb logcat
> trong lúc anh mở game OpenGL bị chớp, đọc log, rồi đề xuất và làm cách sửa. Trước khi chạy bất cứ mã nào tải từ
> ngoài về (ví dụ script dựng Zink trong WSL) thì hỏi anh trước.
