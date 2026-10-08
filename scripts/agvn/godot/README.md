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

Script còn tạo `app/src/main/assets/agvn/godot-license.txt`, gồm `LICENSE.txt` và `COPYRIGHT.txt` của Godot, giấy phép
của Swappy và giấy phép Spine Runtimes. Màn "Giấy phép mã nguồn mở" hiện nguyên văn file này ở cuối trang.

## Spine (từ bản 0.1.31)

Engine có Spine 4.2 (spine-godot dựng vào engine dạng module, như bản "Godot có Spine" của Esoteric), để game Godot làm
bằng Spine Chạy nhẹ được (Mii Chan, 08/10/2026). Script tải `spine-cpp` và `spine-godot` của
https://github.com/EsotericSoftware/spine-runtimes ở đúng commit ghi trong `sources.lock` (git kiểm nội dung theo mã
commit), đặt `spine-godot/spine_godot` cạnh mã nguồn Godot với `spine-cpp` bên trong (như `setup.sh` của Esoteric), áp
các bản vá trong `spine-patches/`, rồi dựng với `custom_modules`. Sau khi dựng, script kiểm tra thư viện có lớp
`SpineSkeletonDataResource` và có phần đọc skeleton của spine-cpp.

- `spine-patches/0001-spine-godot-4.2-on-godot-4.7.patch`: spine-godot 4.2 viết cho Godot tới 4.6. Godot 4.7 bỏ vài
  `#include` gián tiếp, chuyển enum và kiểu của RenderingServer ra ngoài lớp, đổi `free()` thành `free_rid()`, trả `Ref`
  khi `memnew` một lớp RefCounted, và trả danh sách animation library bằng `LocalVector`. Bản vá đổi đúng các chỗ đó như
  nhánh 4.3 của spine-godot đã làm (`#if VERSION_MINOR >= 7`).
- Spine chỉ đọc skeleton xuất từ đúng bản của nó (4.2 đọc 4.2.x). App so bản của game với bản này
  (`AgvnGodotModules.ENGINE_SPINE`); đổi nhánh Spine trong `sources.lock` thì đổi cả hằng số đó.
- **Giấy phép:** Spine Runtimes không phải mã nguồn mở. Phát hành app có Spine cho người chơi không có giấy phép Spine thì
  AGVN phải có giấy phép Spine (xem `docs/agvn/LICENSES.md`).

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
- có các bản vá 0001–0003 (tìm chữ riêng của từng bản trong thư viện). Bản 0004 không thêm chữ nào, nhưng script dừng
  ngay nếu một bản vá không áp được.

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
3. `0003-scripts-of-godot-4.3-and-4.4`: đọc được script dạng mã nhị phân của Godot 4.3 và 4.4 (`.gdc`, phiên bản
   token 100), kiểu Godot 4.3 và 4.4 xuất mặc định. Godot 4.5 trở lên chỉ đọc phiên bản 101 và báo "Binary GDScript is
   not compatible with this engine version". Đã so mã đọc của 4.3, 4.4.1 và 4.7.2. Bản 100 chỉ khác ở hai chỗ:
   - đầu file có thêm một số không dùng trước số token;
   - từ `:` trở đi, mã loại token nhỏ hơn 1, vì Godot 4.5 chèn `...` vào trước `:`.

   Phần còn lại vẫn như cũ: tên, hằng số (`decode_variant`), dòng và cột.
4. `0004-script-classes-of-godot-4.3-and-older`: nhận các class (`class_name`) mà game Godot 4.0–4.3 ghi trong
   `.godot/global_script_class_cache.cfg`. Godot 4.4 thêm hai mục `is_abstract` và `is_tool` vào file này. Từ đó
   Godot bỏ qua class nào thiếu hai mục đó, nên script dùng class của game bị lỗi. Bản vá coi hai mục thiếu là
   `false`, giống Godot 4.3. Lý do: class GDScript chỉ trừu tượng được từ Godot 4.5 (`@abstract`), còn `is_tool` chỉ
   dùng trong editor.

## Cách chạy (Linux x86_64: máy cloud hoặc WSL)

```bash
bash scripts/agvn/cloud-setup.sh       # một lần: Android SDK và NDK 29.0.14206865
scripts/agvn/godot/build-godot.sh      # khoảng 10 phút trên 4 nhân
```

- Mã nguồn đã giải nén được giữ lại trong `build/agvn-godot` khi `sources.lock` và các bản vá không đổi. Bộ nhớ đệm
  của SCons (`build/agvn-godot/scons-cache`) giúp lần dựng sau chỉ biên dịch lại phần thay đổi.
- Script tự ghi SHA-256 mới vào `scripts/agvn/pins.txt`. Commit cả file `.so.gz`, `godot-license.txt` và `pins.txt`.
