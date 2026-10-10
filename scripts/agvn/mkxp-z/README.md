# mkxp-z cho AGVN Player (RPG Maker XP/VX/VX Ace "Chạy nhẹ")

`build-mkxp-z.sh` dựng **mkxp-z** cho Android arm64 thành `app/src/main/jniLibs/arm64-v8a/libmkxp-z.so`. Đây là engine
mã nguồn mở chạy game RPG Maker XP (RGSS1), VX (RGSS2) và VX Ace (RGSS3) mà không cần Windows. App gọi nó từ
`AgvnRgssActivity` trong tiến trình riêng `:rgss` (xem `docs/agvn/engine-rieng.md`, đợt 3).

Script còn chép ra hai thứ:
- `app/src/main/assets/agvn/mkxp-z/*.rb`: ba script nạp trước của mkxp-z (CC0). Chúng thêm lại các hàm Ruby 1.8/1.9 mà
  script RGSS hay dùng, các tên hàm của mkxp cũ, và thay thế các lệnh Win32API phổ biến.
- `app/src/main/assets/agvn/mkxp-z-license.txt`: giấy phép của mkxp-z và mọi thư viện nằm trong `libmkxp-z.so`. Màn
  "Giấy phép mã nguồn mở" hiện nguyên văn file này ở cuối trang.

## Nguồn và cách kiểm chứng (ai cũng làm lại được)

| Thành phần | Nguồn | Kiểm chứng |
|---|---|---|
| mkxp-z 2.4.2 | commit `095b9cc3` của https://github.com/mkxp-z/mkxp-z (nhánh chính ngày 01/10/2026) | Script lấy đúng commit này. |
| Ruby 3.1.3, SDL2 2.28.1, OpenAL Soft, FluidSynth và khoảng 20 thư viện khác | do mkxp-z quản lý bằng file `.wrap` của meson, mỗi nguồn ghim một SHA-256 | `fetch-subprojects.py` kiểm từng file với SHA-256 trong `.wrap` của mkxp-z, sai một byte là dừng. |
| Trình biên dịch | Android NDK 29.0.14206865 (`scripts/agvn/cloud-setup.sh`), API 28 | |
| meson | 1.9.1 từ PyPI, cài vào thư mục làm việc | |

Một số nguồn không tải thẳng được trên máy cloud (chính sách mạng chặn tarball theo thẻ của GitHub,
`gitlab.freedesktop.org`, `cairographics.org`, `freedesktop.org`, `wrapdb.mesonbuild.com`, `skia.googlesource.com`).
`sources.lock` ghi cách lấy từng nguồn:
- `git`: clone đúng commit của thẻ rồi dựng lại tarball như GitHub làm (`git archive | gzip -n`). File ra **giống từng
  byte** file GitHub, nên khớp SHA-256 gốc của mkxp-z. FreeType lấy từ bản sao trên GitHub, file vẫn khớp SHA-256 của
  bản trên GitLab.
- `url`: cùng một file ở nơi khác (`archive.ubuntu.com`), cũng phải khớp SHA-256 gốc.
- File vá của wrapdb lấy từ trang phát hành GitHub của wrapdb. `skcms` (submodule của libjxl) lấy từ bản sao của
  Google trên GitHub.

## Bản vá (`patches/`, áp theo thứ tự, không cho phép xê dịch dòng)

1. `0001-android-build`: dựng `libmkxp-z.so` thay cho file chạy. `SDLActivity` của Android nạp thư viện này và gọi
   `SDL_main`. SDL được liên kết trọn để giữ các hàm JNI, và chỉ xuất `JNI_OnLoad`, `SDL_main` cùng các hàm JNI
   (`android/exports.map`). Bản vá còn:
   - dùng bộ ba hệ đích `aarch64-linux-android`: nhờ vậy Ruby dùng coroutine arm64, vì Android không có
     `pthread_cancel`;
   - thêm tên thư viện FluidSynth cho Android;
   - đưa OpenSL ES của NDK cho OpenAL Soft. NDK không có file pkg-config cho OpenSL ES, nên thiếu bước này thì game
     **không có tiếng**.
2. `0002-sdl-java-package`: các lớp Java của SDL chuyển sang gói `com.winlator.cmod.agvn.sdl`. Nhờ vậy SDL của
   mkxp-z và SDL nằm trong `librenpython.so` của Ren'Py (gói `org.libsdl.app`) cùng có mặt trong app được.
   `app/agvn-rgss.gradle` lấy các lớp Java của đúng SDL 2.28.1 từ gói phát hành của SDL rồi đổi tên gói tương ứng.
3. `0003-sdl-ttf-android`: trên Android, CMake của SDL_ttf đi nhánh riêng cho dự án Android: dựng thư viện chia sẻ
   kèm FreeType 2.10.4 của nó, đè lên FreeType của mkxp-z. Bản vá cho SDL_ttf dựng như trên các hệ khác.
4. `0004-error-file`: khi game dừng vì lỗi, mkxp-z ghi nội dung lỗi vào file `$AGVN_MKXPZ_ERROR_FILE`. App đọc file này
   để phân biệt game lỗi với người chơi tự thoát, rồi gợi ý "Chạy bằng Windows" hoặc cách thêm RTP.

## Cách chạy (Linux x86_64: máy cloud hoặc WSL)

```bash
apt-get install -y git python3-venv ninja-build cmake pkgconf patch ruby bison make binutils
bash scripts/agvn/mkxp-z/build-mkxp-z.sh   # khoảng 30–40 phút với 4 nhân, cần khoảng 6 GB đĩa
```

Trước khi thay file, script tự kiểm:
- `libmkxp-z.so` chỉ cần thư viện có sẵn trên Android (libc, libm, libdl, liblog, libandroid, libGLESv2), không còn
  RUNPATH;
- có `SDL_main`, `JNI_OnLoad` và hàm JNI của gói `com.winlator.cmod.agvn.sdl`, không còn gói `org.libsdl.app`;
- OpenAL có OpenSL ES.

Xong việc, script ghi SHA-256 của `libmkxp-z.so` vào `mkxpz_so_sha256` trong `scripts/agvn/pins.txt`. Bản dựng của
app kiểm SHA-256 này, nên phải commit thư viện và `pins.txt` cùng lúc.

## Lúc chạy trên điện thoại

- App ghi `mkxp.json` vào `files/rgss` của app rồi đặt `SRCDIR` trỏ tới đó. mkxp-z đọc file này, chuyển vào thư mục
  game và đọc `Game.ini` của game.
- **RTP:** tìm theo thứ tự `AGVN-Player/RTP/<tên RTP>`, rồi RTP đã cài trong Wine của game (`C:\Program Files
  (x86)\Common Files\Enterbrain\RGSS[2|3]\<tên>`). RTP không được đóng kèm app vì là phần mềm của Kadokawa có giấy phép
  riêng.
- **Font dự phòng:** WenQuanYi Micro Hei có sẵn trong thư viện, đủ chữ tiếng Việt lẫn tiếng Nhật. Game có thư mục
  `Fonts/` thì dùng font của game.
- **Nhạc MIDI:** FluidSynth với sound font `soundfonts/wt_210k_G.sf2` có sẵn trong app.
- **Nhật ký:** `adb logcat -s mkxp SDL AGVN`. Lỗi làm game dừng còn được lưu vào `AGVN-Player/logs/<tên game>/<giờ>/`
  (`loi-game.txt`, `tom-tat.txt`), để "Gửi nhật ký" gửi kèm.
