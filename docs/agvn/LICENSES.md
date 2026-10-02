# Giấy phép của AGVN Player

Anh Sơn quyết định ngày 02/10/2026: app phát hành theo **GNU GPL phiên bản 3 hoặc mới hơn** (GPL-3.0-or-later).

## Tóm tắt

- **Cả app (file APK) và kho mã này:** GNU GPL 3 hoặc mới hơn.
  - Toàn văn ở [`COPYING`](../../COPYING).
  - Trong app: Giới thiệu → Giấy phép mã nguồn mở, cuối trang (`app/src/main/assets/agvn/gpl-3.0.txt`).
- **Mã gốc của Winlator/Ludashi và mã AGVN viết thêm:** giấy phép MIT ([`LICENSE`](../../LICENSE)). MIT cho phép đưa vào
  app GPL.
  - Giữ nguyên dòng bản quyền của BrunoSX và StevenMXZ.
  - File AGVN mới ghi `Copyright (c) 2026 agvn.io.vn — MIT License`.
- **Thành phần bên thứ ba** giữ giấy phép riêng, danh sách ở `app/src/main/assets/agvn/licenses.html`. Ví dụ:
  - Wine, Proton, VKD3D, PulseAudio: LGPL 2.1.
  - Termux:X11, termux-packages, patchelf: GPL 3.
  - proot: GPL 2.
  - DXVK, D8VK, dxwrapper: zlib.
  - Box64, FEX, Mesa, cnc-ddraw: MIT.
  - libadrenotools: BSD-2-Clause.

## Vì sao chuyển sang GPL

- App đã chứa mã của Termux:X11 (GPL 3).
- Engine riêng sắp làm (mkxp-z cho RPG Maker XP/VX/VX Ace, GPL 2 hoặc mới hơn) chạy chung tiến trình với app. Khi đó
  cả app phải theo GPL. Xem [`engine-rieng.md`](./engine-rieng.md).

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
