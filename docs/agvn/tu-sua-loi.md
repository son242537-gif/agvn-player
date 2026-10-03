# Tự sửa lỗi

Khi game gặp lỗi hoặc chạy chậm, app tự nhận ra và hỏi người chơi theo mẫu: "Máy của bạn đang bị… Bạn có thể…". Áp
dụng cho game Windows và game "Chạy nhẹ" (Ren'Py, RPG Maker XP/VX/VX Ace, RPG Maker MV/MZ và Tyrano). Mỗi cách sửa
là một nút. Bấm nút là app đổi cấu hình của riêng game đó rồi mở lại game. Người chơi không cần vào "Cấu hình".

## Khi nào app hỏi

| Lúc | Ở đâu | Ví dụ |
|---|---|---|
| Trước khi vào game | Hộp hỏi | Tiết kiệm pin đang bật: "Mở cài đặt Tiết kiệm pin" hoặc "Chơi luôn". |
| Đang chơi | Thanh trên game; game vẫn chạy bên dưới | Game chậm đều suốt 1 phút. Sau khi bấm một cách sửa, thanh hỏi "Mở lại game ngay" (nhớ lưu game trước) hay "Để lần sau". |
| Game vừa thoát hoặc bị lỗi | Hộp hỏi ở thư viện (hoặc Big Picture) | Game tắt ngay khi mở, crash, thiếu DirectX, thiếu file `.dll`; game "Chạy nhẹ" báo lỗi script, bị tắt đột ngột, bị treo. Bấm một cách sửa là game mở lại ngay. |
| Android đã tắt game | Hộp hỏi khi về thư viện (trong 24 giờ) | Máy hết RAM; HyperOS tắt game khi chuyển sang app khác. |
| Game Windows vừa hiện khung | Thanh trên game | Khung game nhỏ hơn màn hình: app phóng cho vừa luôn. Khung game lớn hơn màn hình: app đề nghị đổi màn hình game. Xem "Vừa màn hình" bên dưới. |

Mỗi lỗi chỉ hỏi một lần. Cách sửa đã thử mà vẫn lỗi thì lần sau không hiện lại, cho đến khi game chạy tốt một
lần. Thanh game chậm có nút "Để vậy, không hỏi lại" cho riêng game đó.

Thư viện và Big Picture đều hỏi, tùy màn hình nào đang mở: hộp hỏi chỉ hiện ở màn hình đang ở trên cùng.

## Lỗi app nhận ra (`assets/agvn/game-problems.json`)

### Game Windows

| Lỗi | App nhận ra nhờ | Cách sửa (theo thứ tự) |
|---|---|---|
| Game cần Steam | Wine báo thiếu `steam_api.dll` | Gửi nhật ký |
| Thiếu file `.dll` | Wine: `Library X.dll (which is needed by …) not found` | Bật thư viện Windows chứa file đó (DirectX, XAudio, VC++ 2010), gửi nhật ký |
| File `.dll` hỏng hoặc sai 32/64-bit | Wine: lỗi `c000007b` | Gửi nhật ký |
| Game cần .NET | Wine: `Wine Mono is not installed` | Gửi nhật ký |
| Godot không mở được OpenGL | Câu lỗi của Godot, hoặc game Godot tắt trước khi hiện hình | Chạy Godot bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), đổi driver |
| Game bị tắt khi vẽ hình (OpenGL qua Zink) | Zink báo `vkCreateGraphicsPipelines failed` rồi game tự tắt; hay gặp ở GPU Mali, vì Zink thiếu vài tính năng trên đó | Game Godot: chạy bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), bỏ cách chạy Godot đã đổi. Mọi game: đổi driver |
| Không tạo được DirectX / thiếu DirectX 11 | Câu lỗi của DXVK, Unity (`InitializeEngineGraphics failed`) | Đổi bản DXVK, đổi driver (Turnip ↔ System), dùng WineD3D |
| Driver đồ họa lỗi giữa chừng | `VK_ERROR_DEVICE_LOST` | Để Turnip tự chọn chế độ dựng hình, đổi driver, đổi DXVK, hạ Đồ họa |
| Hết bộ nhớ đồ họa / hết RAM | DXVK, Unity, Unreal báo hết bộ nhớ | Hạ Đồ họa |
| File game hỏng | Unity: `is corrupted` | Gửi nhật ký (cần chép lại game) |
| Game không chịu độ phân giải nhỏ ("The current resolution is too low") | Game tắt trước khi hiện hình, màn hình dưới 480 dòng | Nâng Đồ họa (thường là Thấp 854×480), dùng lại cấu hình đã chạy được |
| Game không chạy với cấu hình mới | Lần trước chạy được, đổi cấu hình xong thì tắt trước khi hiện hình | Dùng lại cấu hình đã chạy được, về cấu hình gốc |
| Game crash | Wine in báo cáo crash | Đổi DXVK, giả lập CPU ổn định hơn, đổi driver, về cấu hình gốc |
| Game tắt ngay, không rõ lỗi | Game tắt trước khi hiện hình | Về cấu hình gốc, đổi DXVK, đổi driver, gửi nhật ký |

App biết game Godot là Godot 3 hay 4 nhờ dòng đầu nhật ký của Godot (`godot.log`, app chép vào thư mục phiên chơi), hoặc
nhờ phần đầu file `.pck` của game (file `.pck` riêng hoặc gắn trong file `.exe`).

### Game "Chạy nhẹ"

| Lỗi | App nhận ra nhờ | Cách sửa |
|---|---|---|
| Game báo lỗi script | Ren'Py ghi `traceback.txt` trong lúc chơi; game MV/MZ hiện màn hình lỗi hoặc thiếu file ảnh, dữ liệu (`html-compat.js` giữ câu lỗi) | Chạy bằng Windows, gửi nhật ký |
| Trang game HTML bị tắt | WebView của Android mất trang game (thường do hết RAM) | Chạy bằng Windows, gửi nhật ký |
| Game bị tắt đột ngột | Android ghi tiến trình của Ren'Py hoặc mkxp-z bị lỗi (`CRASH`, `CRASH_NATIVE`) | Chạy bằng Windows, gửi nhật ký |
| Game bị treo | Android ghi tiến trình không phản hồi (`ANR`) | Chạy bằng Windows, gửi nhật ký |

Lỗi mà bộ chạy game đã tự giải thích kèm cách sửa thì không hỏi lại ở thư viện: lỗi của mkxp-z (kể cả hướng dẫn chép
RTP), Ren'Py dừng trước màn hình đầu.

### Cả hai loại game

| Lỗi | App nhận ra nhờ | Cách sửa |
|---|---|---|
| Máy hết RAM nên Android tắt game | Lý do Android ghi lại (`LOW_MEMORY`) | Hạ Đồ họa (game Windows và Ren'Py), gửi nhật ký |
| Máy tắt game khi chạy nền | Lý do Android ghi lại (`SIGKILL` khi game chạy nền) | Mở cài đặt pin của AGVN |
| Game chậm vì GPU | GPU từ 85% trở lên | Hạ Đồ họa, thử chế độ dựng hình Gmem, để Turnip tự chọn, bỏ bớt khung hình (RPG Maker) |
| Game chậm vì CPU | Một nhân CPU từ 90% trở lên | DXVK chạy thẳng trên chip ARM (bản arm64ec), giả lập CPU nhanh hơn, bỏ bớt khung hình (RPG Maker), tắt Tiết kiệm pin |
| Game chậm, máy giấu mức bận của GPU | CPU không bận, nên nhiều khả năng là GPU | Hạ Đồ họa, thử Gmem, DXVK arm64ec, bỏ bớt khung hình |
| Game chậm, không biết GPU hay CPU | Game HTML trên máy giấu mức bận của GPU | Như trên |

Người chơi tự vuốt tắt AGVN khỏi danh sách app gần đây thì không phải lỗi. Khi đó AGVN tự tắt mình (`SIGKILL`, giống
như máy tắt), nên trước khi tắt nó ghi lại là người chơi vuốt tắt. Lần mở sau, app không hỏi gì, và `tom-tat.txt` ghi
"Người chơi vuốt tắt AGVN khỏi danh sách app gần đây". Muốn thoát game thì bấm "✕ Thoát" trên thanh ⌨ ✎ 👁 ⛶ của game
PC (app hỏi lại "Thoát game?"), hoặc "Thoát" trong menu bên trái của game "Chạy nhẹ".

"Game chậm" là game chạy đều dưới mức của nó suốt 1 phút. Màn hình đứng yên (visual novel chờ bấm) và mức "Giới hạn
FPS" người chơi tự đặt không tính.

- **Game Windows:** dưới 20 FPS.
- **RPG Maker XP/VX/VX Ace:** dưới 3/4 tốc độ của game, tức dưới 30 với XP, dưới 45 với VX. FPS lấy từ
  `agvn_fps.rb`, một script nhỏ mkxp-z nạp trước game. HUD giờ cũng hiện FPS của game RPG Maker.
- **Game HTML:** dưới 45 FPS.
- **Ren'Py:** chỉ vẽ khi có gì chuyển động nên không đo FPS. Chỉ tính khi một nhân CPU bận suốt.

Cách sửa nào không đổi được gì thì không hiện. Ví dụ:
- không có nút đổi driver khi máy chỉ có một driver dùng được;
- không có nút hạ Đồ họa xuống mức game đã từ chối;
- không có nút "Chạy bằng Windows" cho bản game không có `.exe`;
- game "Chạy nhẹ" không có các nút chỉnh Wine.

## Mức bận của GPU

App đọc mọi file mà kernel các dòng GPU ghi mức bận, giống HUD của Winlator:
- Adreno (`kgsl`);
- Mali (`utilization`, `gpuinfo`);
- MediaTek (`ged`, `mtk_mali`);
- PowerVR, Exynos;
- mọi nút `devfreq` có tên giống GPU.

Máy nào giấu hết các file này thì app dựa vào CPU: CPU không bận mà game vẫn chậm thì nhiều khả năng do GPU, và app
nói đúng như vậy.

## App biết gì về từng game (`files/agvn/doctor/`)

- **Cấu hình đã chạy được:** cấu hình của lần chơi gần nhất mà game hiện hình và không crash, kéo dài ít nhất 1
  phút hoặc người chơi tự thoát. Nút "Dùng lại cấu hình đã chạy được" đặt lại đúng cấu hình này.
- **Cách sửa đã thử** từ lần chạy tốt gần nhất.
- **Độ phân giải game từ chối:** từ đó hộp Đồ họa ghi "⚠ Lần trước game này không chịu chạy ở độ phân giải này",
  và hộp game chậm không gợi ý hạ xuống mức đó.

"Game đã hiện hình" nghĩa là app thấy cửa sổ game (từ 1/4 màn hình trở lên) được vẽ 20 lần. Hộp lỗi nhỏ của game
không tính. Game tắt khi chưa hiện hình nghĩa là game không mở được, kể cả khi người chơi bấm thoát sau 10 giây mà
chỉ thấy một hộp lỗi.

Game "Chạy nhẹ" chạy trong tiến trình riêng. Lúc game mở, tiến trình đó ghi `files/agvn/light-session.properties`:
game nào, tiến trình nào, game đã hiện màn hình đầu chưa, đã kết thúc bình thường chưa, có lỗi gì. Khi về thư viện,
app đọc file này cùng lý do Android ghi lại cho tiến trình đó. Thời điểm:

- Game ghi "đã kết thúc" ngay lúc màn hình game đóng, trước khi thư viện hiện lại và đọc file.
- Tiến trình chết mà chưa ghi "đã kết thúc": app chờ tối đa 2 giây để Android ghi xong lý do.
- Tiến trình còn sống mà chưa kết thúc (người chơi bấm Home rồi mở thư viện): app không hỏi gì, đợi lần sau.
- "Mở lại game ngay": app đợi tiến trình cũ tắt hẳn (tối đa 12 giây) rồi mới mở lại. Ren'Py và mkxp-z không chạy hai
  lần trong một tiến trình. Yêu cầu mở lại chỉ có hiệu lực 2 phút: người chơi trả lời "không" khi Ren'Py hỏi thoát
  thì lần thoát sau game không tự mở lại.

## Vừa màn hình (game Windows)

Nhiều game mở khung cố định, ví dụ 640×480 hoặc 1280×720. Khi màn hình game (mức "Đồ họa") khác cỡ khung đó, game
hiện thành khung nhỏ ở góc, hoặc bị cắt mất một phần. Mỗi giây, `AgvnScreenFit` tìm khung của game: cửa sổ ứng dụng
lớn nhất, không tính màn nền của Wine (`explorer.exe`). Khi game đã chạy và khung đứng yên 3 giây, app xét
(`AgvnFitMath.verdict`):

| Khung game | App làm gì |
|---|---|
| Nhỏ hơn 85% màn hình cả chiều ngang lẫn chiều dọc | Phóng khung game ra cả màn hình ngay, giữ đúng tỉ lệ (hoặc kéo giãn nếu game đang bật "Kéo giãn"). Chạm vào đâu trên khung là trúng chỗ đó trong game. Thanh báo có "Giữ như vậy" và "Trả lại như cũ". |
| Lớn hơn màn hình | Phần thừa bị cắt, chuột cũng không tới được, nên không phóng nhỏ được. Thanh báo đề nghị "Đổi màn hình game thành <cỡ khung>", rồi "Mở lại game ngay". |
| Vừa | Không làm gì. Khung đổi cỡ sau đó thì app xét lại. |

- Nút ⛶ trên thanh ⌨ ✎ 👁 bật hoặc tắt "Vừa màn hình" bất cứ lúc nào. Khung lớn hơn màn hình thì ⛶ mở thanh đổi màn
  hình.
- Lựa chọn lưu theo game (`agvnFit` trong lối tắt: 1 bật, 0 tắt và không hỏi lại), nên lần sau game mở là vừa luôn.
- Chỉ trình vẽ Vulkan (mặc định của AGVN) phóng được khung. Game chọn EGL hoặc DisplayX thì khung nhỏ cũng được đề nghị
  đổi màn hình.

## Nhật ký Wine luôn có dòng lỗi

Khi tắt "Bật debug Wine", app vẫn cho Wine in hai nhóm lỗi: `err+module` (thiếu hoặc hỏng file `.dll`) và
`err+mscoree` (.NET). Hai nhóm này chỉ in khi có lỗi thật, nên không làm game chậm.

## Thêm một lỗi mới

1. Lấy câu lỗi thật trong `wine-cuoi.txt` hoặc nhật ký engine của người chơi gửi về.
2. Thêm một mục vào `game-problems.json`: `id`, `lines` (biểu thức chính quy), `when`, `title`, `cause` (viết cho
   người chơi), `fixes` (chọn trong các cách sửa đã có).
3. Thêm một ca vào `AgvnDoctorTest` (game Windows) hoặc `AgvnLightDoctorTest` (game "Chạy nhẹ") với đúng câu lỗi đó.
4. Nâng `AGVN_VERSION_CODE` rồi phát hành.

Cách sửa mới (một nút mới) thì cần thêm code ở `AgvnFixes` và `AgvnFixApply`.

## Thử trên máy

- [ ] Đặt Đồ họa Siêu nhẹ cho game báo "The current resolution is too low", mở game, bấm OK. Về thư viện thì có hộp
      "Game không chạy ở độ phân giải 640×360" kèm nút "Đổi Đồ họa sang Thấp (854×480)". Bấm nút, game mở lại và chạy.
      Mở hộp Đồ họa, kéo về Siêu nhẹ: có dòng ⚠.
- [ ] Đổi DXVK của một game đang chạy tốt sang bản game không chạy được. Hộp hỏi có "Dùng lại cấu hình đã chạy được".
- [ ] Game nặng trên máy yếu (GPU 100%): sau khoảng 2,5 phút, thanh "Game đang chạy chậm" hiện với nút hạ Đồ họa và
      nút Gmem. Bấm một nút: thanh "Đã lưu…" hỏi "Mở lại game ngay"; bấm thì game đóng rồi tự mở lại với cấu hình mới.
      Lần khác bấm "Để vậy, không hỏi lại": lần sau không hiện.
- [ ] Game Godot báo "Unable to initialize video driver": hộp hỏi có "Cho game Godot chạy bằng Vulkan".
- [ ] Máy GPU Mali, game Godot bị tắt sau dòng Zink `vkCreateGraphicsPipelines failed` trong `wine-cuoi.txt`: thư viện
      hỏi "Game Godot bị tắt khi vẽ hình". Nút là "Cho game Godot chạy bằng Vulkan" nếu game là Godot 4, hoặc "…GLES2" nếu
      game là Godot 3. Thư mục phiên chơi có `godot.log`.
- [ ] Bật Tiết kiệm pin rồi mở game (Windows và "Chạy nhẹ"): có hộp hỏi trước khi vào game. "Chơi luôn" vào game,
      không có thêm thông báo.
- [ ] Game Ren'Py "Chạy nhẹ" gặp lỗi script (màn hình lỗi của Ren'Py), thoát game: thư viện hỏi "Game báo lỗi khi
      Chạy nhẹ" kèm câu lỗi và nút "Chạy bằng Windows".
- [ ] Game MV "Chạy nhẹ" hiện màn hình lỗi hoặc báo thiếu file, thoát game: thư viện hỏi như trên.
- [ ] Game RPG Maker XP "Chạy nhẹ": HUD hiện FPS (khoảng 40).
- [ ] Game RPG Maker XP hoặc Ren'Py "Chạy nhẹ" chậm: bấm cách sửa trên thanh, rồi "Mở lại game ngay". Game đóng, về
      thư viện vài giây rồi tự mở lại và chơi được (không tắt ngay sau khi mở).
- [ ] Bật Big Picture rồi để một game gặp lỗi: hộp hỏi hiện trong Big Picture.
- [ ] Chơi bình thường rồi thoát (game Windows và "Chạy nhẹ"): không có hộp nào.
- [ ] Game Windows khung 640×480 ở mức "Cao": vài giây sau khi vào game, khung phóng ra cả màn hình, chạm trúng nút
      trong game; "Trả lại như cũ" đưa về khung nhỏ; ⛶ bật lại; mở lại game vẫn vừa.
- [ ] Game Windows khung 1280×720 ở mức "Thấp": thanh "Game bị tràn ra ngoài màn hình", bấm "Đổi màn hình game thành
      1280×720" rồi "Mở lại game ngay": game mở lại thấy đủ khung.
- [ ] Đang chơi một game Windows, vuốt AGVN khỏi danh sách app gần đây rồi mở lại app: không có hộp hỏi; `tom-tat.txt`
      của phiên đó ghi "Người chơi vuốt tắt AGVN…".
- [ ] Game Windows: "✕ Thoát" trên thanh ⌨ ✎ 👁 ⛶ hiện thanh "Thoát game?"; "Chơi tiếp" để game chạy tiếp, "Thoát game"
      đưa về thư viện và không có hộp hỏi.
- [ ] Game mở chậm (hơn 10 giây không vẽ, ví dụ game Unity trên máy yếu): lần mở sau, dòng trên góc phải ghi "Đang khởi
      động 0:25 · lần trước 1:40".
- [ ] `adb logcat -s AGVN`: dòng `doctor: <lỗi> for <game>` và `fix <cách sửa> -> <giá trị>`.
