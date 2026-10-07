# Engine riêng: Ren'Py, RPG Maker XP/VX/VX Ace và Godot

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
| 4 | Godot 4 bằng Godot cho Android | `agvn/p54-godot-native` |

- **Ren'Py làm trước** vì Ren'Py có bản Android chính thức (RAPT), kèm thư viện dựng sẵn. Đợt này nhanh và ít rủi ro.
- **mkxp-z làm sau** vì chưa có bản Android chính thức: phải tự dựng Ruby, SDL2 và mkxp-z cho Android. Đây là việc lớn
  nhất.
- Anh muốn RPG Maker trước thì đổi thứ tự đợt 1–2 với đợt 3.

## Chung cho mọi đợt

- **Người chơi thấy gì:**
  - Game chạy "Chạy nhẹ" như MV/MZ.
  - Nút Quay lại mở menu: mở menu game, thoát, hoặc "Chạy bằng Windows". Đường lui này luôn có, và lối tắt nhớ lựa
    chọn.
- **Bộ công cụ Chạy nhẹ** (anh Sơn chốt 02/10/2026: giống game Windows; `AgvnLightTools`), cho Ren'Py, RPG Maker
  XP/VX/VX Ace và game HTML (MV/MZ, Tyrano):
  - **Phím ảo:** đúng bộ phím game Windows cùng loại (`controls-*.icp`), cùng chỗ, cùng cỡ.
    - Ren'Py, Tyrano: Tiếp, Tua, Lịch sử, Menu, Esc, Space.
    - RPG Maker và MV/MZ: D-pad, OK, Hủy, Chạy, Menu, Enter, Space.
    - Ren'Py nhận "Menu" là chuột phải và "Lịch sử" là lăn chuột lên, như trên PC. RPG Maker XP/VX/VX Ace nhận OK là
      Enter.
  - **Thanh trên cùng:** ⌨ bàn phím Android, ✎ sửa phím, 👁 ẩn/hiện phím (nhớ theo từng game), ☰ menu. Thanh tự ẩn
    sau 3 giây như ở game Windows.
  - **Cây viết ✎ ở góc trên bên trái** (anh Sơn yêu cầu 03/10/2026, `AgvnEditPen`): luôn hiện, chạm là sửa phím. Có ở
    cả game Windows lẫn game "Chạy nhẹ". Chỉ ẩn khi đang sửa phím hoặc khi mở menu bên trái. HUD dời sang phải cây viết.
  - **Menu bên trái** (nút Quay lại hoặc ☰): chơi tiếp, mở menu game, bàn phím, hiện phím ảo, độ mờ phím, sửa phím,
    HUD, "Gửi nhật ký", "Chạy bằng Windows" (nếu có `.exe`), thoát game. Game vẫn chạy phía sau.
  - **Sửa phím** (anh Sơn yêu cầu 03/10/2026: đổi, thêm, xoá phím như JoiPlay; `AgvnLightEditor`). Thanh công cụ giống
    hệt khi sửa phím game Windows:
    - kéo phím để dời; chạm một phím để chọn;
    - "+ Nút": chọn phím trên bàn phím vẽ sẵn, nút mới hiện giữa màn hình; "+ Mũi tên": thêm cụm mũi tên;
    - "Phím…": đổi phím đang chọn sang phím khác, hoặc "Đổi tên" chữ trên phím. Cụm mũi tên đổi được sang W A S D;
    - － ＋ đổi cỡ, "Xóa" xoá phím đang chọn, "Mặc định" (hỏi trước) trả về bộ phím của AGVN, ⇅ dời thanh xuống dưới;
    - "Xong" hoặc nút Quay lại: lưu thành bộ phím riêng của game này, như game Windows có bộ phím riêng.
  - Bàn phím vẽ sẵn chỉ hiện phím game nhận được: RPG Maker XP/VX/VX Ace (mkxp-z) không nhận chuột; game HTML chưa
    nhận các phím dấu câu và bàn phím số. Phím OK của AGVN là Enter ở RPG Maker XP/VX/VX Ace; phím Z người chơi tự chọn
    vẫn là Z.
  - Bộ phím riêng nằm trong `files/agvn-light.properties` (`layout.<thư mục game>`, dạng `.icp`), vì Ren'Py và RPG
    Maker chạy ở tiến trình riêng. Game chưa có bộ phím riêng dùng bộ phím của loại game, với vị trí phím bản 0.1.6–0.1.8
    đã lưu cho cả loại game (`keys.<loại>`).
  - **HUD** (góc trên bên trái): RAM của game (Ren'Py, RPG Maker), RAM trống, pin, nhiệt độ; FPS chỉ đo được ở game HTML.
  - **Bàn phím ở game HTML:** chữ gõ được gửi vào trang thành phím bấm, cho game đọc phím (plugin nhập tên của MV).
- **Gửi nhật ký khi đang chơi:** "Gửi nhật ký" (menu ⋮ của game, menu Chạy nhẹ, hay mục dưới "Xem log" ở menu bên của
  game Windows) gom luôn phiên đang chạy: log engine và log Wine tới lúc đó, logcat của app (mkxp-z, SDL, Ren'Py,
  game HTML) và log của Ren'Py Chạy nhẹ. Không cần thoát game trước.
- **Save, "Nhập save" và "Xuất save"** (menu ⋮ của game; anh Sơn yêu cầu 06/10/2026, bản 0.1.24): save của game
  Chạy nhẹ nằm đúng chỗ bản PC để, nên hai nút này dùng được như ở game Windows (`AgvnSaveLocations`), và đổi qua lại
  "Chạy bằng Windows" vẫn còn save.
  - Ren'Py: `game/saves`. RPG Maker XP/VX/VX Ace: file `Save*.rxdata/.rvdata/.rvdata2` trong thư mục game.
  - Godot: `AppData/Roaming/Godot/app_userdata/<tên dự án>` (hoặc thư mục riêng dự án chọn) trong container Wine. Tên dự
    án đọc trong `project.binary` của gói game (`AgvnGodotUserDir`), như Godot cho Windows tự đặt.
  - RPG Maker MV/MZ: thư mục `save` cạnh `index.html` (MV bản PC: `www/save`), file `file1.rpgsave`, `global.rpgsave`
    (MZ: `file1.rmmzsave`...). Chạy trong trình duyệt, engine vốn để save trong bộ nhớ trình duyệt, nơi không nút nào
    lấy ra được (bản 0.1.23 trở về trước báo "AGVN chưa nhập/xuất được save cho loại game này"). Giờ `html-compat.js`
    chuyển save qua app (`AgvnHtmlSaves`) thành file, đúng chữ bản PC ghi (UTF-8 như NW.js). Save trong trình duyệt từ
    trước được chép ra file ở lần mở đầu, khi thư mục `save` chưa có file nào. Thư mục đã có save (chép từ PC, hay vừa
    "Nhập save") thì lấy save trong thư mục, bỏ qua save cũ trong trình duyệt.
  - Game HTML khác (Tyrano) vẫn để save trong trình duyệt: hai nút báo chưa làm được.
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
  - **Ảnh bìa và icon lấy từ chính game:** `.exe` của game Ren'Py và RPG Maker thường mang icon mặc định của engine, nên
    thư viện, danh sách "Thêm game" và lối tắt màn hình chính dùng ảnh của game:
    - Ren'Py: ảnh nền menu chính (`gui/main_menu`, rồi ảnh có tên kiểu `title`, `menu_bg`, rồi `presplash`), cả khi nằm
      trong gói `game/*.rpa`. Ảnh nền một màu (mặc định của Ren'Py) bị bỏ qua. Icon là `gui/window_icon` nếu nhà làm game
      tự vẽ; icon mặc định của Ren'Py (250x250) bị bỏ qua.
    - RPG Maker MV/MZ: ảnh tiêu đề `data/System.json` chỉ định trong `img/titles1`. Ảnh mã hoá (`.rpgmvp`, `.png_`) đọc
      được mà không cần khoá.
    - RPG Maker XP/VX/VX Ace: ảnh tiêu đề (`Graphics/Titles`, `Graphics/System/Title`, `Graphics/Titles1`), cả khi nằm
      trong gói `.rgssad/.rgss2a/.rgss3a`. Ảnh `Data/System` chỉ định được ưu tiên.
    - Icon của những game này là ô vuông giữa ảnh đó. Không có ảnh nào thì ảnh bìa vẽ tên game, không vẽ icon mặc định
      của RPG Maker. Game đã nhập từ trước tự đổi ảnh bìa và icon một lần sau khi cập nhật app.
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
  10. Thư viện và "Thêm game": game Ren'Py và RPG Maker (cả game đã nhập trước khi cập nhật) có ảnh bìa và icon là màn
      tiêu đề hoặc menu của game, không phải icon Ren'Py hay RPG Maker. Thử thêm một game Ren'Py đóng gói `.rpa` và một
      game MV có ảnh mã hoá.
  11. Bộ công cụ, ở một game Ren'Py, một game RPG Maker XP/VX/VX Ace và một game MV:
      - phím ảo hiện đúng chỗ như khi chạy Windows và bấm được; Tua (giữ) tua chữ, Lịch sử lùi lại ở Ren'Py;
      - ⌨ mở bàn phím, gõ tên được; 👁 ẩn phím, mở lại game vẫn ẩn;
      - ✎: kéo phím, － ＋, "Xong"; mở lại game thấy đúng vị trí mới;
      - cây viết ✎ ở góc trên bên trái luôn hiện, chạm vào là sửa phím; HUD nằm bên phải cây viết, không bị che;
      - ✎ "+ Nút" chọn phím A, "Phím…" đổi "Chạy" sang phím khác, "Xóa" một phím, "Xong": các phím mới bấm được
        trong game, mở lại game vẫn còn; game khác cùng loại vẫn giữ bộ phím của nó; "Mặc định" trả về như cũ;
      - game RPG Maker XP: phím OK vẫn xác nhận; thêm phím Z thì Z không phải Enter; bàn phím vẽ sẵn không có cột chuột;
      - nút Quay lại mở menu bên trái; thử từng mục; HUD hiện RAM, pin, nhiệt độ (FPS ở game MV).
  12. Đang chơi, bấm "Gửi nhật ký" (menu bên của game, hoặc menu ⋮ ngoài thư viện): file zip có `app/logcat.txt` và
      log của phiên đang chạy.

## Đợt 4: Godot 4

Anh Sơn quyết định ngày 05/10/2026, sau khi Party Me (Godot 4.6) đứng hình trên máy Mali-G615 còn app MinHub chạy được:
- làm "Chạy nhẹ" cho game Godot; APK nặng thêm khoảng 24 MB là chấp nhận được;
- cho dựng Godot từ mã nguồn trên máy cloud.

- **Vì sao:** khi chạy bằng Windows, game Godot đi qua Wine và giả lập x86, rồi vẽ qua ANGLE (OpenGL ES trên Direct3D
  11), DXVK (Direct3D 11 trên Vulkan) và wrapper Vulkan. Mỗi hình mới phải dịch shader qua ba lớp. Party Me đứng hình
  10–17 giây lúc vào, rồi 1–5 giây liên tục. Zink và Vulkan đều tắt game trên Mali. "Chạy nhẹ" chạy chính gói game
  (`.pck`) bằng Godot cho Android, vẽ thẳng bằng OpenGL ES hoặc Vulkan của máy.
- **Nguồn:** phần Java là thư viện Android chính thức của Godot 4.7.2 (`org.godotengine:godot`, Maven Central, ghim
  `godot_aar_sha256`). Engine (`libgodot_android.so`) dựng lại từ mã nguồn phát hành của Godot bằng
  `scripts/agvn/godot/build-godot.sh`, vì bản chính thức từ 4.6 không mở gói game ngoài APK. Cách dựng, các bản vá và
  cách kiểm chứng ở [`scripts/agvn/godot/README.md`](../../scripts/agvn/godot/README.md).
- **Giấy phép:** Godot là MIT. Giấy phép đủ của Godot và các thư viện bên trong nằm ở cuối màn "Giấy phép mã nguồn mở".
- **Game nào chạy được** (`AgvnGodotLight.fit`, đọc thư mục của gói như Godot đọc):
  - Godot 4.0–4.7, ví dụ Party Me (4.6). Script dạng chữ (`.gd`) hay dạng mã nhị phân (`.gdc`) đều đọc được. Mã nhị
    phân của Godot 4.3 và 4.4 là bản cũ, mà Godot 4.5 trở lên không đọc được. Engine của app đọc được nhờ bản vá 0003
    (bản 0.1.28; trước đó những game này chạy bằng Windows). Xem "Game Godot 4.0–4.4 Chạy nhẹ" bên dưới.
  - Gói nằm trong `.exe` ("Embed PCK") hay file `.pck` cạnh `.exe`, theo đúng thứ tự Godot cho Windows tìm.
- **Vẫn chạy bằng Windows:**
  - game Godot 3;
  - game Godot viết bằng C# (.NET), tức có thư mục `data_*` chứa `GodotSharp.dll`, hoặc gói có file `.cs`;
  - gói hay file bị mã hoá: chỉ engine riêng của game có khoá;
  - game dùng thư viện native (GDExtension, ví dụ GodotSteam): thư viện đó dựng cho Windows, Android không nạp được;
  - Godot bản mới hơn 4.7.
- **Đã làm** (nhánh `agvn/p54-godot-native`, xếp trên bản 0.1.20, chờ anh Sơn thử máy):
  - **Trong app:** `AgvnGodotActivity` (lớp con `GodotActivity` của Godot) chạy game trong tiến trình riêng `:godot`.
    Dòng lệnh: `--main-pack <gói> --log-file AGVN-Player/godot/<thư mục game>/godot.log --fullscreen`. Có thêm
    `--max-fps` theo giới hạn FPS của mức đồ hoạ.
  - **Nhận diện:** game Godot được đặt "Chạy nhẹ" khi nhập, nếu Godot 4.7 chạy được nó. Game Godot đã nhập từ trước
    (chưa chọn cách chạy) tự chuyển ở lần mở tiếp theo; lối tắt nhớ lựa chọn này.
  - **Save và thiết lập:** cùng chỗ với "Chạy bằng Windows" (`AppData/Roaming/Godot/app_userdata/<project>` trong
    container Wine của game), nhờ bản vá 0001. Đổi cách chạy vẫn còn save.
  - **File cạnh `.exe`:** game nạp gói vá, DLC hay bản dịch đặt cạnh `.exe` vẫn tìm thấy (bản vá 0002).
  - **Cách vẽ:** dùng cách vẽ của chính game (Godot đổi Forward+ thành Mobile trên Android, tức Vulkan). Vulkan không mở
    được thì Godot tự quay về OpenGL ES.
  - **Phím ảo:** bộ phím chọn lúc nhập (mặc định là bộ 2D cho game Godot). Chạm vào màn hình là chuột trái. Phím ảo
    chuột phải và cuộn chuột bấm ở chỗ vừa chạm.
  - **Nút Quay lại:** cùng menu với Ren'Py và RPG Maker: mở menu game (Esc), ẩn/hiện phím, bàn phím, thoát, "Chạy bằng
    Windows". Godot không tự thoát khi bấm Quay lại.
  - **Game dừng trước khi hiện hình:** app hiện lỗi đầu tiên trong `godot.log` và gợi ý "Chạy bằng Windows".
  - **Không mở trang web:** link game mở (`OS.shell_open`, ví dụ trang Steam) bị chặn.
  - **Nhật ký:** `adb logcat -s godot AGVN`; `AGVN-Player/godot/<thư mục game>/godot.log`. "Gửi nhật ký" kèm file này.
- **Thử máy:** Party Me trên máy Mali-G615, thêm một game Godot 4.5–4.7 khác nếu có.
  1. Cài đè bản đang dùng. Mở Party Me đã nhập từ trước: game chạy "Chạy nhẹ". Có dòng `Godot 4.6.x game ... on Chạy
     nhẹ` trong `adb logcat -s AGVN`.
  2. Thời gian vào game và các lần đứng hình: so với `su-kien.txt` của bản 0.1.20 (đứng 10–17 giây lúc vào).
  3. Chơi 10 phút: chạm để bấm, kéo, chuyển cảnh, nghe nhạc. Lưu, thoát, mở lại, tải save.
  4. Save cũ khi chạy bằng Windows có trong game. Lưu khi "Chạy nhẹ", rồi "Chạy bằng Windows": vẫn thấy save đó.
  5. Nút Quay lại: thử từng mục. Esc mở menu game. Thoát game xong thì thư viện hiện lại.
  6. Bấm Home rồi quay lại game: game chạy tiếp, có tiếng.
  7. RAM và nhiệt: `adb shell dumpsys meminfo com.agvn.player:godot`; so với khi chạy bằng Windows.
  8. Chữ tiếng Việt của bản dịch AGVN hiện đúng; `godot.log` có `AGVN font` và `AGVN splash`.
  9. Hồi quy: Ren'Py 8, RPG Maker XP/VX/VX Ace, MV/MZ "Chạy nhẹ" và game Windows vẫn chạy như cũ.
  10. Màn "Giấy phép mã nguồn mở" có phần Godot.
  11. Các màn của app vẫn như cũ: thư viện, "Thêm game", menu ⋮ của game, cấu hình lối tắt, tạo container, Cài đặt.
      Thư viện Godot cần AndroidX Fragment 1.8.6 (trước là 1.4.0) và Kotlin stdlib 2.1.21, nên cả app dùng bản mới này.

## Save của game Chạy nhẹ (bản 0.1.24)

Chỗ để save của từng loại game: xem "Save, Nhập save và Xuất save" ở phần chung bên trên.

- **Thử máy:**
  1. Game MV "Chạy nhẹ" đã chơi và lưu bằng bản 0.1.23 (save còn trong trình duyệt): cài đè bản mới, mở game một lần,
     vào màn "Tải": save cũ vẫn còn. Thư mục `www/save` (game MV bản PC) hoặc `save` của game có `file1.rpgsave`,
     `global.rpgsave`, `config.rpgsave`.
  2. Lưu vào một ô mới, thoát. Menu ⋮ của game → "Xuất save": file .zip trong `AGVN-Player/Saves/<game>/` có
     `game/www/save/...` (hoặc `game/save/...`).
  3. Chép save của game đó từ PC (`www/save/*.rpgsave`, cả `global.rpgsave`: MV chỉ hiện những ô có trong file này)
     vào máy, rồi "Nhập save" chọn các file đó. Mở game: màn "Tải" có các save của PC.
  4. Game MZ: như các bước 1–3, với `save/*.rmmzsave`.
  5. Lưu khi "Chạy nhẹ", rồi "Chạy bằng Windows": thấy save đó, ở MV/MZ, Ren'Py, RPG Maker XP/VX/VX Ace và Godot.
  6. Godot (Party Me): "Xuất save" có `profile/AppData/Roaming/Godot/app_userdata/<tên dự án>/...`. "Nhập save" file
     .zip đó trên máy khác: save hiện trong game.
  7. Ren'Py, RPG Maker XP/VX/VX Ace "Chạy nhẹ": "Xuất save" rồi "Nhập save" trên máy khác: save hiện trong game.
  8. Game Tyrano: "Nhập save" / "Xuất save" báo chưa làm được, như trước.

## Game RPG Maker MZ đứng ở cảnh đầu (bản 0.1.25)

Anh Sơn báo 07/10/2026 (POCO F8 Pro, bản 0.1.24): mọi game MZ "Chạy nhẹ" đứng ở cảnh đầu (màn đen, logo), chạm không
đi tiếp, và không có lỗi nào nên "Tự sửa lỗi" không biết.

- **Vì sao:** MZ chỉ cập nhật cảnh khi cửa sổ đang được chọn (`SceneManager.isGameActive` hỏi
  `window.top.document.hasFocus()`), như game trên PC đứng chờ khi người chơi chọn cửa sổ khác. Từ Android 9, ở chế độ
  chạm không view nào tự có focus, và chạm vào WebView cũng không cho focus, nên trang luôn trả lời "không được chọn":
  game vẫn vẽ nhưng cảnh không chạy tiếp. MV không hỏi điều này nên không bị.
- **Sửa:**
  - `html-compat.js` (mục 7): trang được coi là "đang được chọn" khi đang hiện (`document.hasFocus()` theo
    `document.visibilityState`). Hàm của MZ, cả khi plugin bọc lại, và plugin nào hỏi `hasFocus` đều theo đó, từ trước
    khi script đầu tiên của game chạy. Rời app thì trang ẩn và game vẫn dừng như trước.
  - `AgvnHtmlTyping`: WebView nhận focus khi game mở và mỗi lần cửa sổ có lại focus (đóng hộp thoại, kéo thanh thông
    báo, quay lại từ app khác), trừ lúc ô gõ chữ đang giữ để mở bàn phím ảo. Phím của bàn phím rời và tay cầm nhờ vậy
    tới được trang.
  - Không thêm bộ dò "cảnh đứng" cho "Tự sửa lỗi": sau khi sửa, `isGameActive()` chỉ còn sai khi plugin của game cố ý
    dừng cảnh (ví dụ lúc chiếu phim), nên bộ dò sẽ báo nhầm cho game đang chạy tốt.
  - Test: `tools/agvn/tests/html_focus_sim.js` chạy vòng cảnh của MZ trên trang không có focus (trước khi sửa: cảnh
    không chạy).
- **Thử máy:**
  1. Một game MZ bất kỳ: tự qua màn giới thiệu tới màn tiêu đề mà không cần chạm; vào chơi, lưu, tải được.
  2. Có bật nhật ký (DevTools): `SceneManager.isGameActive()` là `true` khi game đang hiện. Về màn hình chính thì game
     dừng (tiếng tắt, cảnh không chạy), quay lại thì chạy tiếp.
  3. Game MZ có ô nhập tên: bấm ⌨ rồi gõ: chữ vào ô, game vẫn chạy trong lúc gõ. Đóng bàn phím rồi bấm phím rời hay tay
     cầm: game nhận phím.
  4. Mở menu Chạy nhẹ (nút Quay lại) rồi đóng: game chạy tiếp, phím rời vẫn tới game.
  5. Hồi quy: game MV (tiếng, khung hình, save) và game Tyrano vẫn như cũ.

## Plugin dùng `process` của NW.js (bản 0.1.26)

Anh Sơn báo 07/10/2026 (POCO F8 Pro, bản 0.1.25): game RPG Maker có plugin đọc `process` ngay lúc nạp file (ví dụ
plugin sao lưu thư mục `data`: `path.dirname(process.mainModule.filename)`) dừng ở màn đen với chữ "ReferenceError /
process is not defined", và "Tự sửa lỗi" không biết game dừng vì đâu.

- **Vì sao:** `html-compat.js` có `require('fs'/'path'/'nw.gui'/'os')` nhưng không có `process`, để engine không tưởng
  mình chạy trên NW.js (`Utils.isNwjs()` hỏi `typeof process === "object"`) mà bỏ đường save của trình duyệt. Plugin
  viết cho NW.js thấy `require` nên dùng tiếp `process` và lỗi. Lỗi lúc nạp không qua `SceneManager.catchException`:
  MZ (`main.js`) giữ lỗi đầu tiên, nạp xong thì chỉ hiện màn lỗi và không chạy cảnh đầu. MV chạy tiếp, với plugin đó
  chạy dở.
- **Sửa (`html-compat.js`):**
  - `process` là một hàm, không phải object: `Utils.isNwjs()`, `main.js` và plugin nào thử `typeof process === "object"`
    vẫn thấy trình duyệt, nên save vẫn qua `AgvnSaves`. Có `mainModule.filename` (`/index.html`: thư mục `/` là thư mục
    game, như với `fs` và `path`), `platform` (`android`), `cwd()` (`/`), `env` (rỗng), `argv`, `versions` (không có
    `node` hay `nw`), `execPath`, `on`/`once`/`off`... (không làm gì, trả lại `process`), `exit` (không làm gì, như
    `nw.App.quit`), `nextTick`, `memoryUsage`, `stdout`/`stderr.write`.
  - `fs` có thêm `copyFileSync`, `renameSync`, `appendFileSync`, `rmSync`, `rmdirSync`, `lstatSync`, `accessSync` và
    bản bất đồng bộ. Bản chép được ghi vào localStorage như mọi file plugin ghi; `readdirSync` vẫn trả rỗng nên plugin
    sao lưu cả thư mục không chép gì.
  - Lỗi đầu tiên lúc nạp script được giữ kèm tên file và dòng. Khi MZ hiện màn lỗi mà chưa có cảnh nào chạy, lỗi đó vào
    `__agvnFatal` để "Tự sửa lỗi" nói được game dừng vì plugin nào. Game MV chạy tiếp sau lỗi đó nên không bị coi là
    dừng. App đọc `__agvnFatal` mỗi 2 giây (trước là 5), để người chơi thoát ngay khi thấy màn lỗi vẫn được hỏi.
  - Cần để ý: plugin chỉ thử `typeof process !== "undefined"` để biết có phải NW.js không giờ sẽ đi đường PC, dùng
    `fs`/`path` của app. Cách thử phổ biến (`Utils.isNwjs()`, `typeof process === "object"`, `process.versions.nw`)
    vẫn đi đường trình duyệt.
  - Test: `tools/agvn/tests/html_compat_sim.js` (plugin thử của báo cáo, các hàm `fs` mới), `html_load_error_sim.js`
    (lỗi lúc nạp ở MZ và MV). Cả hai không qua với `html-compat.js` của bản 0.1.25.
- **Thử máy:**
  1. Plugin thử (trong báo cáo) bật trong `plugins.js` của một game MZ và một game MV: game vào màn tiêu đề, không có
     màn lỗi; logcat có `[THU] base=/ platform=android cwd=/`.
  2. DevTools: `typeof process` là `"function"`, `Utils.isNwjs()` là `false`; MZ: `StorageManager.isLocalMode()` là
     `false`.
  3. Lưu ván: có `save/file1.rmmzsave` (MZ) hay `save/file1.rpgsave` (MV); save cũ trên máy tải được.
  4. Plugin cố tình lỗi khi nạp (`khongCo.x = 1;` ở đầu file) trong game MZ: vẫn hiện màn lỗi như cũ; thoát game sau
     vài giây, "Tự sửa lỗi" hiện `ReferenceError: khongCo is not defined (<tên file>.js:1)`.
  5. Hồi quy: game không dùng `process` không đổi gì (tiếng, khung hình, save, "Thoát game").

## Game Godot 4.0–4.4 Chạy nhẹ (bản 0.1.28)

Sau nhật ký Train45 ngày 07/10/2026 (game Godot qua Wine khựng 1–10 giây), anh Sơn bảo làm mọi cách để game chạy mượt
nhất. Game Godot 4.3 và 4.4 trước đây luôn chạy bằng Windows, chậm như vậy.

- **Vì sao chưa chạy được:**
  - Godot 4.3 và 4.4 mặc định xuất script dạng mã nhị phân phiên bản 100. Godot 4.5 đổi sang phiên bản 101 và chỉ
    đọc bản đó ("Binary GDScript is not compatible with this engine version").
  - Godot 4.0–4.3 ghi danh sách class của game (`class_name`, trong `.godot/global_script_class_cache.cfg`) thiếu
    hai mục mà Godot 4.4 trở lên đòi. Godot 4.7 bỏ qua mọi class trong danh sách đó, nên script dùng class của game
    bị lỗi. Lỗi này có cả ở game Godot 4.0–4.2 vốn đã Chạy nhẹ được.
- **Sửa (engine, `scripts/agvn/godot/patches`):**
  - `0003`: đọc cả script phiên bản 100. Đã so mã đọc của Godot 4.3, 4.4.1 và 4.7.2. Hai bản chỉ khác ở đầu file
    (thêm một số không dùng) và mã loại token: Godot 4.5 chèn `...` vào trước `:`, nên từ `:` trở đi mã của bản cũ
    nhỏ hơn 1. Mọi loại token của 4.4 đã được đối chiếu đúng với loại cùng tên ở 4.7. Phần đọc tên, hằng số
    (`decode_variant`), dòng và cột vẫn như 4.4.
  - `0004`: class thiếu hai mục `is_abstract` và `is_tool` vẫn được nhận, hai mục đó coi là `false`, giống Godot 4.3.
  - Đã kiểm thêm trong mã của Godot 4.7: gói `.pck` kiểu cũ (format 2, của Godot 4.0–4.4) và cache UID vẫn đọc như
    trước; scene và resource dạng nhị phân chỉ bị từ chối khi mới hơn engine.
  - Thư viện mới xuất đúng 1022 hàm như bản trước. Chỉ 6 file của Godot được biên dịch lại.
- **Trong app:** `AgvnGodotLight.fit` nhận script phiên bản 100 (`OLD_TOKENS`), cùng 101.
  - Game Godot 4.3, 4.4 thêm mới được đặt Chạy nhẹ lúc nhập.
  - Game đã nhập mà chưa chọn cách chạy tự chuyển ở lần mở tiếp theo.
  - Game đã đặt "Chạy bằng Windows" giữ nguyên. "Tự sửa lỗi" và hộp hỏi khi game chậm đề nghị "Chạy nhẹ" cho game đó.
  - Phiên bản script lạ (không phải 100, 101) vẫn chạy bằng Windows (`UNKNOWN_SCRIPTS`).
- **Không chắc chắn:** game Godot 4.0–4.4 chạy trên engine 4.7, không phải engine của chính game. Godot giữ tương thích
  giữa các bản 4.x nên phần lớn game chạy được, nhưng game dựa vào hành vi đã đổi có thể lỗi riêng.
  - Game dừng trước khi hiện hình: app hiện lỗi đầu tiên trong `godot.log`, kèm nút "Chạy bằng Windows".
  - Game vẫn chạy nhưng lỗi: "Tự sửa lỗi" có cách "Chạy bằng Windows".
- **Thử máy:** một game Godot 4.3 hoặc 4.4 (game xuất mặc định có file `.gdc`), thêm một game 4.0–4.2 dùng
  `class_name` nếu có.
  1. Thêm game: game được đặt Chạy nhẹ. `adb logcat -s AGVN` có `Godot 4.x.y game ... on Chạy nhẹ`, không có
     `goes to Wine`.
  2. Game vào màn tiêu đề và chơi được 10 phút. `godot.log` không có "Binary GDScript is not compatible", không có
     "Identifier ... not declared" hay "Could not find type".
  3. Game 4.3/4.4 đã nhập trước khi cập nhật:
     - chưa chọn cách chạy: lần mở tiếp theo tự Chạy nhẹ;
     - đã đặt "Chạy bằng Windows": "Tự sửa lỗi" → "Game chậm, giật, lag" có cách "Chạy nhẹ" đầu tiên. Thử cách đó:
       game mở bằng Godot cho Android.
  4. So với khi chạy bằng Windows: thời gian vào game, các lần khựng, RAM (`dumpsys meminfo com.agvn.player:godot`).
  5. Save khi chạy bằng Windows vẫn thấy khi Chạy nhẹ, và ngược lại.
  6. Hồi quy: Party Me (Godot 4.6) vẫn Chạy nhẹ như cũ. Game Godot 3 và game Godot C# vẫn chạy bằng Windows.

## Bộ phím chỉnh sẵn tới được game (bản 0.1.29)

Anh Sơn báo ngày 07/10/2026: chỉnh bộ phím "Nym" ở màn chỉnh phím của Winlator rồi vào game thì game không có phím.

Có hai chỗ app làm mất bộ phím đã chỉnh, và một trạng thái giữ phím ẩn:
- **Chỉnh từ trong game Windows** (menu bên trái → Điều khiển → ⚙, hay hộp "Điều khiển"): khi quay lại game, phím
  biến mất. Lỗi có từ Winlator gốc. Lúc quay lại, Winlator ẩn bộ phím rồi dựng lại danh sách hồ sơ, chọn theo bộ phím
  đang hiện. Lúc đó không còn bộ nào hiện, nên danh sách chọn "Tắt" và game chạy tiếp không có phím. Giờ màn chỉnh
  phím báo lại hồ sơ đang mở lúc đóng (`MainActivity.agvnReturnProfile`). Game hiện lại hồ sơ đó, kèm phần vừa chỉnh,
  và nhớ nó cho game (`XServerDisplayActivity.agvnShowEditedControls`).
- **Game "Chạy nhẹ"** (Ren'Py, RPG Maker, Godot, HTML) bỏ qua mục "Cấu hình phím ảo" trong cài đặt game. Game luôn
  dùng bộ phím AGVN theo loại game, nên bộ phím làm ở màn "Điều khiển" không bao giờ tới game.
  - Giờ bộ phím của game bắt đầu từ hồ sơ được chọn (`AgvnLightPick`). Chỉ nút và D-pad dùng được; cần analog và
    vùng chạm thì không, vì game "Chạy nhẹ" không có chuột ảo.
  - Bộ phím riêng sửa trong game (✎) vẫn được dùng nếu nó được làm từ hồ sơ đang chọn (`layoutBase.<game>` trong
    `agvn-light.properties`). Chọn hồ sơ khác thì game dùng hồ sơ mới.
  - "Mặc định" trong lúc sửa đưa phím về hồ sơ đang chọn.
  - "Tắt" mở game với phím ẩn. Để "Tự động" thì như trước: bộ phím AGVN theo loại game.
- **Phím đã ẩn** (👁 Ẩn) giữ ẩn ở mọi lần mở sau. Giờ chọn một bộ phím cho game trong cài đặt (trừ "Tắt") thì bộ phím
  hiện lại, ở cả game Windows lẫn "Chạy nhẹ".

- **Thử máy:**
  1. Game Windows: menu bên trái → Điều khiển → ⚙ → sửa một hồ sơ (thêm một nút) → quay lại game. Bộ phím vừa sửa hiện
     ngay, có nút mới. Thoát rồi mở lại game: vẫn bộ phím đó.
  2. Ở màn "Điều khiển" ngoài thư viện, tạo một hồ sơ có vài nút và D-pad. Vào cài đặt một game "Chạy nhẹ" (RPG Maker
     hoặc Ren'Py) → Điều khiển → "Cấu hình phím ảo" chọn hồ sơ đó. Mở game: thấy đúng các nút và D-pad đó, bấm được.
  3. Ở game đó, ✎ sửa một phím rồi "Xong". Mở lại game: thấy phím đã sửa. Đổi "Cấu hình phím ảo" sang hồ sơ khác:
     game dùng hồ sơ mới. Đổi về "Tự động": game dùng bộ phím AGVN như trước.
  4. Ẩn phím bằng 👁 trong một game, thoát. Chọn một bộ phím cho game đó trong cài đặt rồi mở game: phím hiện.
  5. Hồi quy: game "Chạy nhẹ" không chọn gì (Tự động) vẫn dùng bộ phím AGVN và bộ phím riêng đã sửa trước đây.
