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
| 3 | RPG Maker XP/VX/VX Ace bằng mkxp-z | `claude/great-goodall-qjemo1` |

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
- **Đã làm** (nhánh `agvn/p50-renpy8`, chờ anh Sơn thử máy):
  - **Dựng app:** `app/agvn-renpy.gradle` tải RAPT và SDK, kiểm SHA-256 theo `scripts/agvn/pins.txt`, rồi lấy ra:
    - `librenpython.so` và mã Java SDL2, pyjnius đi kèm, giữ nguyên như Ren'Py dựng;
    - `assets/agvn/renpy8.zip`: phần engine (`renpy/` đã dịch sẵn, `lib/python3.12/`) cùng `main.py` của AGVN;
    - giấy phép của Ren'Py, hiện trong màn "Giấy phép mã nguồn mở".

    Máy dựng chỉ chép file ra khỏi gói, không chạy mã tải về.
  - **Trong app:** `AgvnRenpyActivity` chạy game trong tiến trình riêng `:renpy`. Lần đầu mở game Ren'Py, app giải
    nén engine (khoảng 23 MB) vào bộ nhớ trong của app. Các lần sau chỉ giải nén lại khi bản cập nhật app đổi engine.
  - **Nhận diện:** game Ren'Py 8 (có `lib/py3-*` hoặc `lib/python3.*`) được đặt "Chạy nhẹ" khi nhập game. Ren'Py 6, 7
    vẫn chạy bằng Windows. Game đã nhập trước bản này vẫn chạy Windows; nhập lại (Cập nhật) để chuyển.
  - **Save:** nằm trong `game/saves` của thư mục game, đúng chỗ bản PC và "Chạy bằng Windows" dùng. Đổi cách chạy vẫn
    còn save, và xuất/nhập save ở menu ⋮ dùng được như cũ.
  - **Nhật ký:** `log.txt`, `traceback.txt` ở `AGVN-Player/renpy/<tên thư mục game>/`; logcat thẻ `python`.
  - **Nút Quay lại:** cùng menu với MV/MZ:
    - "Mở menu trong game": phím Esc.
    - "Thoát game": game tự hỏi và lưu dữ liệu như khi đóng cửa sổ trên PC.
    - "Chạy bằng Windows": lối tắt nhớ lựa chọn này.
  - **Game không mở được:** app báo lỗi, chỉ chỗ nhật ký và gợi ý "Chạy bằng Windows".
  - **Liên kết web trong game** (Patreon, Discord...): không mở, theo luật 3.
  - **compileSdk:** AGVN giữ 35; mã SDL của Ren'Py 8.5.3 biên dịch được với SDK 35.
  - **APK:** nặng thêm 22,6 MB (556,8 MB so với 534,2 MB của 0.1.5).
- **Thử máy:** cần 3 game Ren'Py 8 khác nhau.
  1. Cài đè bản đang dùng. Nhập 3 game; bảng xem trước ghi "Cách chạy: Chạy nhẹ".
  2. Mỗi game:
     - mở game (lần đầu chờ app chuẩn bị Ren'Py);
     - chơi 10 phút, lưu, tải lại;
     - thử nút Quay lại với cả ba mục, rồi "Thoát game".
  3. Lưu khi "Chạy nhẹ", rồi "Chạy bằng Windows": vẫn thấy save đó.
  4. Bấm Home rồi quay lại game: game chạy tiếp, có tiếng.
  5. So RAM và nhiệt với khi chạy bằng Windows: `adb shell dumpsys meminfo com.agvn.player:renpy`.
  6. Game Ren'Py 7 vẫn chạy bằng Windows.
  7. Hồi quy: game MV/MZ "Chạy nhẹ" và game Windows vẫn chạy như cũ.
  8. Màn "Giấy phép mã nguồn mở" có phần Ren'Py.

  Nhật ký khi lỗi: `adb logcat -s python AGVN SDL`.

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

Anh Sơn chốt ngày 02/10/2026:
- làm đợt 3 ngay sau Ren'Py 8, trước Ren'Py 7;
- cho dựng mkxp-z trên máy cloud;
- game thiếu RTP: app tự tìm RTP, thiếu thì báo bằng tiếng Việt.

- **Nguồn:** mkxp-z 2.4.2 (commit `095b9cc3`), cùng Ruby 3.1.3, SDL2 2.28.1, OpenAL Soft, FluidSynth, PhysFS, FreeType,
  SDL_image/sound/ttf và các thư viện khác do mkxp-z ghim. Cách lấy nguồn, 4 bản vá và cách kiểm chứng ở
  [`scripts/agvn/mkxp-z/README.md`](../../scripts/agvn/mkxp-z/README.md).
- **Giấy phép:** mkxp-z dựng kèm shader GPLv3, nên là GPL-3.0-or-later, hợp với app (đợt 0). Giấy phép đủ của mkxp-z và
  mọi thư viện trong `libmkxp-z.so` nằm ở cuối màn "Giấy phép mã nguồn mở".
- **Đã làm** (nhánh `claude/great-goodall-qjemo1`, xếp trên chuỗi Ren'Py, chờ anh Sơn thử máy):
  - **Dựng:** `scripts/agvn/mkxp-z/build-mkxp-z.sh` ra `app/src/main/jniLibs/arm64-v8a/libmkxp-z.so`. Thư viện chỉ cần
    thư viện có sẵn của Android. SHA-256 ghi ở `mkxpz_so_sha256` trong `scripts/agvn/pins.txt`, bản dựng của app kiểm
    lại.
  - **Trong app:** `AgvnRgssActivity` chạy game trong tiến trình riêng `:rgss`. Lớp Java của SDL 2.28.1 do
    `app/agvn-rgss.gradle` lấy từ gói phát hành của SDL, đổi sang gói `com.winlator.cmod.agvn.sdl` để không đụng SDL
    của Ren'Py.
  - **Nhận diện:** game có `Game.ini` (hoặc `<tên exe>.ini`) với `RGSS1/2/3*.dll`, file `.rgssad/.rgss2a/.rgss3a` hay
    `Data/*.rxdata/.rvdata/.rvdata2` được đặt "Chạy nhẹ" khi nhập. RPG Maker 2000/2003 và MV/MZ không đổi. Game đã nhập
    trước bản này vẫn chạy Windows; nhập lại (Cập nhật) để chuyển.
  - **Bản cho điện thoại (không có `.exe`, kiểu gói JoiPlay):** quét tự động và "Chọn thư mục khác" đều nhận được game
    RPG Maker XP/VX/VX Ace, MV/MZ, Tyrano và Ren'Py 8 không có `.exe`. Trong "Chọn thư mục khác", chạm vào
    `index.html`, `Game.ini` hoặc file `.rgss*a` để thêm đúng game đó. Những game này chỉ chạy bằng Chạy nhẹ, nên menu
    và hộp thoại lỗi không có mục "Chạy bằng Windows".
  - **Quét sâu hơn:** gốc bộ nhớ quét 3 tầng (`RPG/Việt hoá/Game`), AGVN, Download, Games và thư mục tự chọn 4 tầng.
  - **Máy yếu và trung bình:** bật bỏ khung hình (`frameSkip`): khi máy chậm hơn trọn một khung hình, mkxp-z bỏ vẽ khung
    đó để game giữ đúng tốc độ thay vì chạy chậm lại; máy kịp thì không có tác dụng.
  - **Save:** nằm trong thư mục game như trên PC và khi "Chạy bằng Windows", nên đổi cách chạy vẫn còn save.
  - **RTP:** tìm theo thứ tự `AGVN-Player/RTP/<tên>` rồi RTP đã cài trong Wine của game. Không đóng RTP vào app.
  - **Phím ảo:** D-pad và các nút OK, Hủy, Chạy, Menu, đặt đúng chỗ như bố cục RPG khi chạy Windows. OK gửi Enter, vì
    trong XP phím Z là nút chạy nhanh. Ẩn/hiện được trong menu nút Quay lại. Tay cầm và bàn phím thật cũng dùng được.
  - **Nút Quay lại:** cùng menu với Ren'Py: mở menu game (nút B), ẩn/hiện phím ảo, thoát game, "Chạy bằng Windows".
  - **Game dừng vì lỗi:** app hiện lỗi gốc của game, gợi ý "Chạy bằng Windows". Nếu lỗi là thiếu file và máy chưa có RTP
    game cần, app chỉ chỗ chép RTP.
  - **Chữ và nhạc:** font dự phòng WenQuanYi Micro Hei đủ tiếng Việt và tiếng Nhật; nhạc MIDI qua FluidSynth với sound
    font có sẵn trong app. Nạp sẵn 3 script của mkxp-z cho tương thích (hàm Ruby cũ, Win32API).
  - **Nhật ký:** `adb logcat -s mkxp SDL AGVN`. Game dừng vì lỗi thì lỗi gốc và bản tóm tắt (RGSS mấy, RTP tìm được
    hay thiếu) được lưu vào `AGVN-Player/logs/<tên game>/<giờ>/`, nên "Gửi nhật ký" ở menu ⋮ của game có cả lỗi này.
- **Thử máy:** cần một game XP, một game VX, một game VX Ace (tốt nhất một game dùng nhạc MIDI và một game cần RTP).
  1. Cài đè bản đang dùng. Nhập 3 game; bảng xem trước ghi "Cách chạy: Chạy nhẹ".
  2. Mỗi game:
     - mở game, nghe nhạc nền và tiếng động;
     - đi bằng D-pad, giữ "Chạy", mở menu bằng "Hủy"/"Menu", xác nhận bằng "OK";
     - chơi 10 phút, lưu, thoát, mở lại, tải save.
  3. Nút Quay lại: thử cả bốn mục.
  4. Lưu khi "Chạy nhẹ", rồi "Chạy bằng Windows": vẫn thấy save đó.
  5. Game cần RTP mà máy chưa có: app báo cách chép RTP. Chép RTP vào `AGVN-Player/RTP/<tên>`, mở lại: game chạy.
  6. Bấm Home rồi quay lại game: game chạy tiếp, có tiếng.
  7. So RAM và nhiệt với khi chạy bằng Windows: `adb shell dumpsys meminfo com.agvn.player:rgss`.
  8. Hồi quy: game RPG Maker 2000/2003, MV/MZ "Chạy nhẹ", Ren'Py 8 và game Windows vẫn chạy như cũ.
  9. Màn "Giấy phép mã nguồn mở" có phần mkxp-z.
