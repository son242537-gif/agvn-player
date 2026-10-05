# Godot cho AGVN Player (game Godot 4 "Chạy nhẹ")

`build-godot.sh` dựng engine **Godot 4.7.2** cho Android arm64. Kết quả là `app/agvn-godot/libgodot_android.so.gz`
(bản đã strip, nén gzip). App chạy nó từ `AgvnGodotActivity` trong tiến trình riêng `:godot` (xem
`docs/agvn/engine-rieng.md`, đợt 4).

## Vì sao phải dựng lại

Godot có sẵn bản Android chính thức (`org.godotengine:godot` trên Maven Central, giấy phép MIT). Nhưng từ Godot 4.6, bản
đó chỉ mở được gói game nằm trong APK. Lý do: bản xuất cho người chơi được dựng với `disable_path_overrides=yes`, nên
`--main-pack /storage/...` báo "attempting to load from outside of the executable" rồi dừng.

Script này dựng đúng phiên bản đó từ mã nguồn phát hành của Godot, với `disable_path_overrides=no`. Phần Java vẫn là thư
viện chính thức cùng phiên bản: `app/agvn-godot.gradle` tải nó (ghim `godot_aar_sha256`), rồi thay thư viện engine bên
trong bằng bản này (ghim `godot_so_sha256`). Hai bên gọi nhau theo tên hàm JNI, nên phải cùng phiên bản: đổi phiên bản
ở `sources.lock` thì đổi cả `godotVersion` trong `app/agvn-godot.gradle`.

Script còn tạo `app/src/main/assets/agvn/godot-license.txt`, gồm `LICENSE.txt` và `COPYRIGHT.txt` của Godot cùng giấy
phép của Swappy. Màn "Giấy phép mã nguồn mở" hiện nguyên văn file này ở cuối trang.

## Nguồn và cách kiểm chứng

| Thành phần | Nguồn | Kiểm chứng |
|---|---|---|
| Godot 4.7.2 | `godot-4.7.2-stable.tar.xz` trên trang phát hành của Godot | SHA-256 trong `sources.lock`, trùng file `.sha256` và `SHA512-SUMS.txt` Godot đăng kèm |
| Swappy (Android Game SDK) | `godot-swappy.zip`, bản `from-source-2025-01-31` của https://github.com/godotengine/godot-swappy | SHA-256 trong `sources.lock`; đúng bản `misc/scripts/install_swappy_android.py` của Godot 4.7.2 tải |
| SCons 4.11.1 | PyPI, cài vào thư mục làm việc | `pip --require-hashes` với SHA-256 trong `sources.lock` |
| Trình biên dịch | Android NDK 29.0.14206865, đúng bản Godot 4.7.2 đòi (`platform/android/detect.py`) | |

Lệnh dựng giống bản chính thức (`production=yes`, có Swappy), thêm `disable_path_overrides=no`. Sau khi dựng, script
kiểm tra:
- thư viện chỉ cần các thư viện có sẵn trên Android, giống bản chính thức;
- có các hàm JNI mà phần Java gọi;
- không còn câu chặn gói game ngoài APK;
- có hai bản vá bên dưới.

Đã so với bản chính thức 4.7.2: hai thư viện xuất đúng 1022 hàm như nhau.

## Bản vá (`patches/`, áp theo thứ tự, không cho phép xê dịch dòng)

1. `0001-user-dir-as-on-windows`: khi có biến môi trường `AGVN_GODOT_APPDATA`, `user://` nằm đúng chỗ Godot cho Windows
   dùng: `%APPDATA%/Godot/app_userdata/<tên project>`, hoặc thư mục riêng game đặt (`custom_user_dir_name`). App đặt
   biến này là thư mục `AppData/Roaming` trong container Wine của game. Nhờ vậy:
   - save và thiết lập của game dùng chung giữa "Chạy nhẹ" và "Chạy bằng Windows";
   - mỗi game có một chỗ riêng. Không có bản vá thì mọi game Godot cùng ghi vào thư mục `files` của app.
2. `0002-executable-path-of-the-windows-game`: `OS.get_executable_path()` trả về file `.exe` của game
   (`AGVN_GODOT_EXECUTABLE`). Game nào tìm gói vá, DLC, bản dịch hay mod đặt cạnh file `.exe` thì vẫn thấy. Gọi lại
   đường dẫn đó (game tự khởi động lại) vẫn là khởi động Godot, như với `"apk"` của bản gốc.

## Cách chạy (Linux x86_64: máy cloud hoặc WSL)

```bash
bash scripts/agvn/cloud-setup.sh       # một lần: Android SDK và NDK 29.0.14206865
scripts/agvn/godot/build-godot.sh      # khoảng 10 phút trên 4 nhân
```

- Mã nguồn đã giải nén được giữ lại trong `build/agvn-godot` khi `sources.lock` và các bản vá không đổi. Bộ nhớ đệm
  của SCons (`build/agvn-godot/scons-cache`) giúp lần dựng sau chỉ biên dịch lại phần thay đổi.
- Script tự ghi SHA-256 mới vào `scripts/agvn/pins.txt`. Commit cả file `.so.gz`, `godot-license.txt` và `pins.txt`.
