# Engine riêng: Ren'Py và RPG Maker XP/VX/VX Ace

Anh Sơn quyết định ngày 02/10/2026:
- làm lại kế hoạch engine riêng, mỗi engine một đợt;
- mỗi đợt thử trên máy thật trước khi phát hành;
- hai engine: RPG Maker XP/VX/VX Ace và Ren'Py;
- app chuyển sang giấy phép GPL (xem [`LICENSES.md`](./LICENSES.md)).

## Vì sao

- **Hiện tại:** game Ren'Py và RPG Maker XP/VX/VX Ace chạy bằng Windows, tức Wine cộng giả lập x86. Cách này tốn RAM,
  nóng máy và hao pin.
- **Sau khi làm:** game chạy thẳng trên Android bằng engine gốc, như "Chạy nhẹ" của MV/MZ, nên nhẹ hơn nhiều.
- **Nguồn mã:** không dùng mã của JoiPlay (phần mềm đóng). Chỉ dùng mã nguồn mở lấy từ nguồn chính thức, khoá phiên
  bản bằng SHA-256.

## Thứ tự

| Đợt | Việc | Nhánh |
|---|---|---|
| 0 | Giấy phép GPL 3 cho cả app | `agvn/p49-gpl-license` |
| 1 | Ren'Py 8: game Python 3 (Ren'Py 8.x, phần lớn game mới) | `agvn/p50-renpy8` |
| 2 | Ren'Py 7: game Python 2 (Ren'Py 6.x và 7.x) | `agvn/p51-renpy7` |
| 3 | RPG Maker XP/VX/VX Ace bằng mkxp-z | `agvn/p52-mkxp-z` |

- **Ren'Py làm trước** vì Ren'Py có bản Android chính thức (RAPT), kèm thư viện dựng sẵn. Đợt này nhanh và ít rủi ro.
- **mkxp-z làm sau** vì chưa có bản Android chính thức: phải tự dựng Ruby, SDL2 và mkxp-z cho Android. Đây là việc lớn
  nhất.
- Anh muốn RPG Maker trước thì đổi thứ tự đợt 1–2 với đợt 3.

## Chung cho mọi đợt

- **Người chơi thấy gì:**
  - Game chạy "Chạy nhẹ" như MV/MZ.
  - Nút Quay lại mở menu: mở menu game, thoát, hoặc "Chạy bằng Windows". Đường lui này luôn có, và lối tắt nhớ lựa
    chọn.
- **Nhận diện:** `GameExeResolver` đã nhận ra engine (`RENPY`, `RPGMAKER`). Cần thêm phần đọc phiên bản:
  - Ren'Py: `renpy/__init__.py` hoặc thư mục `lib/py3-*` / `lib/py2-*`.
  - RPG Maker: RGSS1, 2 hay 3, theo `Game.ini` và `RGSS*.dll`.
  - Game chưa hỗ trợ thì vẫn chạy bằng Windows như cũ.
- **Tiến trình riêng:** engine chạy trong tiến trình riêng (`android:process`). Engine thoát hay văng thì app không
  chết theo.
- **Mỗi đợt:**
  - Làm trên một nhánh riêng, có test đơn vị và checklist thử máy.
  - Chỉ gộp vào bản phát hành sau khi anh Sơn thử xong trên máy thật.
- **Nguồn tải:**
  - Chỉ tải từ trang chính thức, và ghi phiên bản cùng SHA-256 vào `scripts/agvn/pins.txt`.
  - Không chạy mã tải về trên máy dựng khi anh Sơn chưa đồng ý.

## Đợt 0: GPL 3

- **Thay đổi:**
  - `COPYING`: toàn văn GPL 3.
  - Trong app: màn "Giấy phép mã nguồn mở" có toàn văn GPL 3 ở cuối, và câu giới thiệu đổi sang GPL 3.
  - `CLAUDE.md` và [`LICENSES.md`](./LICENSES.md) cập nhật theo.
- **Không đổi gì cho người chơi.** Đi cùng bản phát hành đầu tiên có engine riêng.

## Đợt 1: Ren'Py 8

- **Nguồn:** Ren'Py 8.5.3, phát hành ngày 16/05/2026. SHA-256 khớp `checksums.txt` của renpy.org.
  - `renpy-8.5.3-rapt.zip`: `8a12be34a2f5238d125ff6dd76a56772fd8e44838af864f81bca82c1059b00e6`.
  - `renpy-8.5.3-sdk.tar.bz2`: `eb0a9be7f0fb13632fe25ceade9a8bed5a1b4d6b6e83bd19eeeb29e1a1bb4a45`.
- **Có sẵn trong RAPT:**
  - `librenpython.so` cho arm64, 36 MB: gồm Python 3, phần C của Ren'Py và SDL2.
  - Mã Java: `SDLActivity`, `PythonSDLActivity`.
- **Có sẵn trong SDK:** `renpy/` (engine) và `lib/` (thư viện Python).
- **Cách làm:**
  - Thêm một Activity riêng trong AGVN, dựa trên `PythonSDLActivity`.
  - `main.py` của AGVN trỏ Ren'Py vào thư mục game của người chơi và đọc `game/` thẳng từ bộ nhớ. Không đóng gói lại
    từng game như RAPT làm.
- **Việc cần giải quyết:**
  - RAPT dùng compileSdk 36, AGVN đang dùng 35.
  - Chỗ lưu game: trong thư mục game hay trong app; save xuất/nhập (menu ⋮) phải dùng được.
  - Nút Quay lại và menu.
  - Bàn phím ảo: không cần, vì Ren'Py hỗ trợ chạm sẵn.
  - APK nặng thêm khoảng 40–60 MB.
- **Thử máy:**
  - 3 game Ren'Py 8 khác nhau: mở game, chơi 10 phút, lưu, tải lại, thoát.
  - So RAM và nhiệt với khi chạy bằng Windows.
  - Game Ren'Py 7 phải được nhận ra và vẫn chạy bằng Windows.

## Đợt 2: Ren'Py 7

- **Nguồn:** Ren'Py 7.8.7, phát hành cùng ngày với 8.3.7 (17/03/2025).
  - `renpy-7.8.7-rapt.zip`: `b212bba59594d976afaee4f30eb9ee2d0170bf030d240b0e165d8b422b722a3a`.
  - `renpy-7.8.7-sdk.tar.bz2`: `65466068af7c181a143f13d84b0ddf7a6aa58dd8d26e7ccd97cdcadecabcae5a`.
- **Vấn đề:** hai bản Ren'Py dùng chung tên lớp Java (`org.libsdl.app.SDLActivity`,
  `org.renpy.android.PythonSDLActivity`), nhưng mã có thể khác nhau, mà một APK chỉ chứa được một bản. Hai hướng giải:
  - dùng cặp 8.3.7 + 7.8.7, cùng mã Java (`renpy-8.3.7-rapt.zip`:
    `ea3a96dc59a276464fcec30eb5371e12178be563348b1e318a5f236c3511ef9f`);
  - hoặc kiểm tra xem mã Java của 8.5.3 có chạy được với thư viện của 7.8.7 không.

  Quyết định ở đầu đợt 2.
- **Thử máy:** 3 game Ren'Py 6 hoặc 7.

## Đợt 3: RPG Maker XP/VX/VX Ace (mkxp-z)

- **Nguồn:** mkxp-z (GPL 2 hoặc mới hơn), Ruby, SDL2, OpenAL Soft, PhysFS, SDL_sound và các thư viện liên quan. Tất cả
  khoá theo commit.
- **Việc cần làm:**
  - Dựng toàn bộ cho Android arm64 bằng NDK của app.
  - Thêm Activity riêng.
  - Phím ảo: dùng bố cục RPG sẵn có của AGVN.
- **RTP:** nhiều game cần gói RTP chuẩn của RPG Maker. RTP có giấy phép riêng của Kadokawa, nên không đóng gói vào app.
  Cách xử lý game thiếu RTP sẽ chốt ở đầu đợt.
- **Rủi ro:** lớn nhất trong ba đợt, cần nhiều phiên làm việc.
- **Thử máy:** 3 game, một game XP, một game VX, một game VX Ace.
