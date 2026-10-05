# Giấy phép của AGVN Player

Anh Sơn quyết định ngày 02/10/2026: app phát hành theo **GNU GPL phiên bản 3 hoặc mới hơn** (GPL-3.0-or-later).

## Tóm tắt

- **Cả app (file APK) và kho mã này:** GNU GPL 3 hoặc mới hơn.
  - Toàn văn ở [`COPYING`](../../COPYING).
  - Trong app: Giới thiệu → Giấy phép mã nguồn mở, cuối trang (`app/src/main/assets/agvn/gpl-3.0.txt`).
- **Mã gốc của Winlator/Ludashi và mã AGVN viết thêm:** giấy phép MIT ([`LICENSE`](../../LICENSE)). MIT cho phép đưa vào
  app GPL.
  - Giữ nguyên dòng bản quyền của BrunoSX và StevenMXZ.
  - File AGVN mới ghi `Copyright (c) 2026 agvn.io — MIT License`.
- **Thành phần bên thứ ba** giữ giấy phép riêng, danh sách ở `app/src/main/assets/agvn/licenses.html`. Ví dụ:
  - Wine, Proton, VKD3D, PulseAudio: LGPL 2.1.
  - Proton 10.0-4 arm64ec (Wine thứ hai, cho game mới và game có phim WMV3 mà Proton 9 không phát được): LGPL 2.1.
    App giữ nguyên gói `proton-10.0-4-arm64ec.wcp` của bản phát hành `build-bionic-layers-20260930-v9` tại
    https://github.com/The412Banner/proton-wine (SHA-256 ghim trong `scripts/agvn/pins.txt`); mã nguồn đúng bản này là
    nhánh `proton_10.0` của kho đó, dựng từ Proton 10 của Valve (https://github.com/ValveSoftware/wine).
  - Wine Mono 9.3.1 (bộ chạy .NET cho Wine, chỉ giải nén cho game cần .NET): nhiều giấy phép, theo `COPYING` của nó.
    Phần Mono là MIT X11 hoặc LGPL; FNA là MS-PL và MIT; FAudio, SDL2 và các thư viện đi kèm FNA là zlib; WinForms và
    WPF là MIT; SharpZipLib là GPL có ngoại lệ. Wine Mono chạy riêng trong Wine, không liên kết vào app. App giữ
    nguyên file phát hành gốc `wine-mono-9.3.1-x86.tar.xz`. Mã nguồn đúng bản này là `wine-mono-9.3.1-src.tar.xz`
    trên trang phát hành https://github.com/wine-mono/wine-mono/releases/tag/wine-mono-9.3.1; kho mã:
    https://gitlab.winehq.org/mono/wine-mono.
  - Termux:X11, termux-packages, patchelf: GPL 3.
  - proot: GPL 2.
  - DXVK, D8VK, dxwrapper: zlib.
  - Box64, FEX, Mesa, cnc-ddraw: MIT.
  - libadrenotools: BSD-2-Clause.
  - Ren'Py 8.5.3 ("Chạy nhẹ" cho game Ren'Py): MIT. `librenpython.so` của Ren'Py chứa thêm Python (PSF), SDL2 (zlib),
    pyjnius (MIT), FFmpeg và FriBiDi (LGPL 2.1), OpenSSL (Apache 2.0) cùng các thư viện khác. Danh sách đủ nằm trong
    `LICENSE.txt` của Ren'Py; app hiện nguyên văn file này ở cuối màn giấy phép. Mã nguồn:
    https://github.com/renpy/renpy và https://github.com/renpy/renpy-build.
  - Godot Engine 4.7.2 ("Chạy nhẹ" cho game Godot 4): MIT. Phần Java là thư viện Android của chính Godot
    (`org.godotengine:godot`); `libgodot_android.so` được AGVN dựng lại từ mã nguồn chính thức (`disable_path_overrides=no`,
    thêm các bản vá trong `scripts/agvn/godot/patches`) và chứa thêm FreeType, HarfBuzz, ICU, mbedTLS, Swappy (Apache 2.0)
    cùng các thư viện khác. Giấy phép đầy đủ: `app/src/main/assets/agvn/godot-license.txt` (LICENSE.txt và COPYRIGHT.txt của
    Godot, giấy phép của Swappy), tạo bởi `scripts/agvn/godot/build-godot.sh`. Mã nguồn: https://github.com/godotengine/godot;
    bản vá và script dựng: `scripts/agvn/godot`.
  - mkxp-z ("Chạy nhẹ" cho game RPG Maker XP/VX/VX Ace): GPL 3 hoặc mới hơn (dựng kèm shader GPLv3). `libmkxp-z.so`
    chứa thêm Ruby (Ruby License / BSD-2-Clause), SDL2, SDL_image, SDL_sound, SDL_ttf, PhysFS (zlib), OpenAL Soft và
    FluidSynth (LGPL 2.1), FreeType (FTL), pixman, fmt, libffi, libyaml (MIT), libogg/vorbis/theora, libjxl (BSD) cùng
    các thư viện khác. Danh sách và giấy phép đầy đủ: `app/src/main/assets/agvn/mkxp-z-license.txt`, tạo bởi
    `scripts/agvn/mkxp-z/licenses.sh`. Mã nguồn: https://github.com/mkxp-z/mkxp-z; bản vá và script dựng cho Android:
    `scripts/agvn/mkxp-z`.

## Vì sao chuyển sang GPL

- App đã chứa mã của Termux:X11 (GPL 3).
- Engine riêng mkxp-z (RPG Maker XP/VX/VX Ace, GPL) là thư viện app nạp vào tiến trình của mình (`:rgss`), nên cả app
  phải theo GPL. Xem [`engine-rieng.md`](./engine-rieng.md).

## Nghĩa là gì

- **Cho người chơi:** không đổi gì. App vẫn miễn phí.
- **Mã nguồn kèm mỗi bản:** mỗi bản phát hành phải có mã nguồn tương ứng.
  - Thẻ `v0.1.x` trên GitHub là mã của đúng APK đó: script `tools/agvn/dang-ban-cap-nhat.ps1` tạo release từ đúng
    commit đã dựng, và GitHub tự kèm "Source code".
  - Không được xoá hay ẩn kho mã.
- **Người khác dùng mã:** ai cũng được sao chép, sửa và phát hành lại app, miễn là giữ GPL và công khai mã của họ.
- **Khoá ký:** vẫn giữ riêng trên máy anh Sơn. GPL 3 chỉ bắt đưa thông tin cài đặt khi phần mềm được bán kèm thiết
  bị, không áp dụng cho app tải về riêng.

## Khi lấy thêm mã của người khác

- **Được dùng:** chỉ mã có giấy phép tương thích GPL 3, gồm MIT, BSD, zlib, Apache 2.0, LGPL, "GPL 2 hoặc mới hơn"
  và GPL 3.
- **Chương trình chạy riêng** (ví dụ proot) chỉ đi kèm app, không ảnh hưởng giấy phép của app.
- **Không dùng:**
  - Mã đóng, ví dụ JoiPlay và MTool.
  - Mã có giấy phép cấm sửa hoặc cấm thương mại.
- **Nguồn tải:** chỉ tải từ nguồn chính thức, và ghi phiên bản cùng SHA-256 vào `scripts/agvn/pins.txt`.
