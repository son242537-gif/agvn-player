# Kế hoạch AGVN Player cho iOS (chưa bắt đầu)

Lưu ngày 25/11/2026 theo lời anh Sơn: "lưu dự án lại, nào cần thì khởi động". Chưa có dòng mã iOS nào. Anh đã có tài
khoản Apple Developer Program (99 USD/năm).

Những điều đánh dấu **(kiểm lại)** là hiểu biết tới giữa 2026, phải xem lại khi bắt đầu, vì Apple và các engine đổi
nhanh.

## 1. Chạy được gì, không chạy được gì

| Loại game | Trên iOS chạy bằng gì | Khả năng |
|---|---|---|
| HTML (RPG Maker MV/MZ, Tyrano) | WKWebView (JavaScript có JIT của chính Apple); dùng lại `html-compat.js`, `html-frame.js` | Dễ nhất, làm trước |
| Ren'Py | Ren'Py có hỗ trợ iOS chính thức (renios); sửa thành bộ chạy mở thư mục game bất kỳ | Được |
| Godot 4 | Bản iOS của Godot; game GDScript được, game C# thì không | Được |
| RPG Maker XP/VX/VX Ace | mkxp-z (Ruby) tự dựng cho iOS; dùng lại các script giả hàm Windows của AGVN | Được nhưng vất hơn |
| KiriKiri | Bộ chạy KiriKiri viết lại (từ mã nguồn mở KiriKiri Z), không có Wine | Chỉ một phần game |
| Game Windows (Wine + FEX/Box64) | Không | Không làm |
| Game Unity port sang APK | Không dùng lại được APK; mỗi game phải dựng lại bằng IL2CPP | Chỉ từng game Mono, rất tốn công |
| Wolf RPG | Chỉ có bản Windows | Không |

### Vì sao game Windows không chạy được trên iPhone

1. **iOS cấm JIT.** Chỉ WebKit của Apple, và trình duyệt khác ở EU (BrowserEngineKit), được ghi mã mới vào bộ nhớ rồi
   chạy. FEX/Box64 dịch x86 sang ARM lúc chạy, tức là JIT.
   - Không có JIT thì chỉ còn thông dịch, chậm 10–50 lần. UTM SE trên App Store chạy Windows XP rất ì ạch.
   - Mẹo bật JIT cho app cài ngoài (gắn trình gỡ lỗi, StikDebug) bị chặn chặt hơn từ iOS 26 trên máy đời mới, chỉ còn
     chạy với vài app được làm riêng **(kiểm lại)**.
2. **Không có Linux bên trong.** iOS không phải Linux, cấm proot (ptrace). App không được tạo tiến trình con, mà Wine
   cần wineserver và nhiều tiến trình.
3. **Không có Vulkan,** chỉ có Metal. Turnip và DXVK cần Vulkan, phải qua MoltenVK. D3DMetal của Apple chỉ có trên Mac.
4. **Wine cho máy Apple chỉ có trên Mac** (CrossOver, Whisky), dựa vào Rosetta 2. iPhone không có Rosetta.

Giả lập máy cũ (NES tới PSP) vẫn chạy được trên iOS bằng thông dịch, vì chip gốc của chúng yếu. GameCube, Wii, Switch
và PC thì cần JIT.

### Game Unity port sang APK

- Android dùng lại DLL C# của game với Mono (JIT). iOS chỉ có IL2CPP: DLL phải được chuyển sang C++ bằng Unity Editor
  đúng phiên bản (có module iOS), rồi biên dịch bằng Xcode. Mỗi game là một app riêng, một Bundle ID riêng.
- Game PC đã là IL2CPP (`GameAssembly.dll`): không còn DLL để chuyển, gần như không làm được.
- Shader: bản PC có DirectX (đôi khi OpenGL), không có Metal, nên phải thay hoặc dựng lại shader.
- Texture DXT/BC của bản PC: có thể phải giải nén lúc tải, tốn RAM **(kiểm lại)**.
- Game của hãng khác và game người lớn gần như chắc chắn bị Apple từ chối ngoài thử nội bộ.

### Lưu ý từng engine

- **HTML:**
  - RPG Maker MZ tự giải mã .ogg (WebAssembly), nên có tiếng trên mọi máy.
  - RPG Maker MV nhờ trình duyệt giải mã. WebKit trên iOS đọc .ogg từ bản nào thì chưa chắc **(kiểm lại)**. Nếu không
    đọc được, phải đổi âm thanh sang .m4a lúc thêm game.
  - WebM cho phim cũng cần kiểm lại.
- **Ren'Py:** game Ren'Py 7 (Python 2) cần cách xử lý như bản Android. Game kèm module Python đã biên dịch thì không chạy.
- **mkxp-z:**
  - Chưa có bản iOS chính thức **(kiểm lại)**.
  - Vẽ bằng OpenGL, trên iOS phải qua ANGLE hoặc OpenGL ES cũ.
  - Giấy phép GPL-3.
- **KiriKiri:** nhiều game cần plugin DLL của Windows, phải tự viết lại. Nhiều game thương mại mã hoá `.xp3` theo cách
  riêng của từng game. Chỉ game không mã hoá, dùng plugin phổ biến mới chạy.

## 2. Thứ tự làm

1. App iOS chạy game HTML (RPG Maker MV/MZ):
   - Chọn thư mục game từ app Tệp.
   - WKWebView, file game phục vụ qua `WKURLSchemeHandler` như `shouldInterceptRequest` của Android.
   - Dùng lại `html-compat.js`, `html-frame.js`. Có phím ảo cơ bản.
2. Ren'Py và Godot.
3. RPG Maker XP/VX/VX Ace (mkxp-z).
4. KiriKiri: thử vài game cụ thể trước rồi mới hứa.

App iOS là app riêng, viết bằng Swift trong thư mục `ios/` của kho. Mã AGVN dùng giấy phép MIT. Bước 1 không có thành
phần GPL.

**Dùng lại được từ bản Android:**
- Các script trang (`app/src/main/assets/agvn/html-*.js`).
- `game-profiles.json` và danh mục lỗi (`game-problems*.json`).
- Chữ tiếng Việt và các ngôn ngữ khác.
- Cách nhận ra engine (viết lại bằng Swift).

**Phải bỏ hoặc đổi:**
- **Tự cập nhật từ GitHub:** iOS chỉ cập nhật qua kho.
- **Chạy nền:** iOS dừng app sau vài giây ở nền.
- **Thư mục game:** không có `/sdcard`; người chơi chép game qua app Tệp vào thư mục của app.
- **Giải nén:** 7-Zip phải gắn vào app như thư viện, không chạy như chương trình riêng được.
- **Giao diện:** phím ảo, HUD, "Tự sửa lỗi" viết lại.

## 3. Dựng và thử (không cần máy Mac)

- Máy cloud của Claude chạy Linux, không có Xcode. App được dựng trên máy macOS của GitHub Actions (miễn phí với kho
  công khai). Claude chạy được các lần dựng và đọc nhật ký qua công cụ GitHub, sửa lỗi theo vòng như bản Android.
- **Mỗi lần dựng:**
  1. **Dựng và chạy trên iPhone ảo (Simulator):** không cần khoá. Claude tự kiểm bằng game HTML mẫu và test tự động.
  2. **Ký và đẩy lên TestFlight:** cần khoá API của tài khoản Apple. Số bản dựng tăng theo số lần chạy.
     `ITSAppUsesNonExemptEncryption = NO` trong Info.plist để khỏi hỏi về mã hoá.
- Anh cài app TestFlight trên iPhone. Mỗi bản mới tới máy sau khi Apple xử lý xong, khoảng 10–30 phút.
- Apple bắt dựng bằng Xcode và SDK mới; từ tháng 4/2026 là Xcode 26 **(kiểm lại)**. Không bắt người chơi dùng iPhone
  mới.

### Anh cần làm một lần, trên web

1. developer.apple.com → Certificates, Identifiers & Profiles → Identifiers → "+" → App IDs: Bundle ID, ví dụ
   `com.agvn.player`.
2. appstoreconnect.apple.com → Apps → "+" → New App: iOS, tên AGVN Player (trùng thì thêm chữ), Bundle ID trên, SKU tuỳ
   ý.
3. Users and Access → Integrations → App Store Connect API → Team Keys: tạo khoá quyền Admin (để Xcode tự xin chứng chỉ
   ký). Tải tệp `.p8` (chỉ tải được một lần), ghi lại Key ID và Issuer ID.
4. Kho GitHub → Settings → Secrets and variables → Actions: tạo `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (nội dung tệp
   `.p8`). GitHub mã hoá các mục này; chúng không nằm trong mã nguồn.
5. App Store Connect → app → TestFlight → Internal Testing: thêm Apple ID của anh. Cài app TestFlight trên iPhone.

### Việc anh cần quyết trước khi bắt đầu

`CLAUDE.md` có luật: bản dựng trên cloud không ký bằng khoá phát hành. Khoá API ở bước 3 cho GitHub Actions ký và đẩy
lên TestFlight. Có hai cách:

- **Cho phép riêng iOS (Claude đề nghị):** khoá nằm trong mục bí mật của GitHub, chỉ dùng để đẩy bản thử lên TestFlight,
  thu hồi được bất cứ lúc nào. Ghi quyết định vào `CLAUDE.md`.
- **Giữ luật:** cloud chỉ dựng bản chưa ký. Anh ký và đẩy lên TestFlight bằng một máy Mac (của anh hoặc thuê).

## 4. Phát hành

- **TestFlight thử nội bộ:** tối đa 100 người trong nhóm, Apple không duyệt, mỗi bản dùng 90 ngày. Hợp cho giai đoạn
  thử.
- **TestFlight mời người ngoài:** tối đa 10.000 người, Apple duyệt từng bản, kể cả nội dung.
- **App Store:**
  - Mục 1.1.4 cấm nội dung khiêu dâm. App không chứa game, nhưng cách giới thiệu không được nhắc tới game người lớn.
  - Mục 2.5.2 cấm chạy mã tải về; mục 4.7 (từ 2024) mở cho trình giả lập và mini game HTML5. Không chắc được duyệt.
  - GPL xung đột với điều khoản App Store (VLC bị gỡ năm 2011).
- **Kho thay thế ở EU** (AltStore PAL): chỉ người ở EU tải được.
- **Cài ngoài** (AltStore, SideStore): ký lại mỗi 7 ngày, khó với người chơi không rành.

**Anh cần quyết:**
- Đi đường nào.
- Bản iOS có giữ cách giới thiệu game người lớn như bản Android không.

## 5. iPhone tối thiểu

Đề nghị **iOS 16 trở lên: iPhone 8, 8 Plus, X trở đi.** Có thể phải nâng lên iOS 17 (iPhone XS/XR) khi thêm Ren'Py,
Godot hay mkxp-z **(kiểm lại mức tối thiểu của từng engine)**.

| Bản iOS | iPhone cũ nhất |
|---|---|
| iOS 15 | iPhone 6s |
| iOS 16 | iPhone 8, 8 Plus, X |
| iOS 17, 18 | iPhone XS, XR |
| iOS 26 | iPhone 11 |

RAM quan trọng hơn chip: iOS tắt thẳng app dùng quá nhiều bộ nhớ.

| RAM | Máy | Chơi được |
|---|---|---|
| 2–3 GB | iPhone 8, X, XR | Visual novel, RPG Maker nhẹ; game nhiều ảnh lớn có thể bị tắt |
| 4 GB | iPhone XS, 11, 12, 13 | Phần lớn game |
| 6–8 GB | iPhone 13 Pro, 14, 15, 16 trở lên | Thoải mái |

iPad chạy được, cùng điều kiện. Cần người trong nhóm thử có máy 2–3 GB.

## 6. Khi khởi động lại

1. Đọc lại tài liệu này, kiểm lại các mục **(kiểm lại)**.
2. Anh làm 5 bước ở mục 3 và quyết luật ký ở mục 3, đường phát hành ở mục 4.
3. Claude làm bước 1 (game HTML):
   - Dựng trên iPhone ảo trước.
   - Khi có khoá thì đẩy lên TestFlight.
