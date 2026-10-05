# Tự sửa lỗi

Khi game gặp lỗi hoặc chạy chậm, app tự nhận ra và hỏi người chơi theo mẫu: "Máy của bạn đang bị… Bạn có thể…". Áp
dụng cho game Windows và game "Chạy nhẹ" (Ren'Py, RPG Maker XP/VX/VX Ace, RPG Maker MV/MZ và Tyrano). Mỗi cách sửa
là một nút. Bấm nút là app đổi cấu hình của riêng game đó rồi mở lại game. Người chơi không cần vào "Cấu hình".

## Khi nào app hỏi

| Lúc | Ở đâu | Ví dụ |
|---|---|---|
| Trước khi vào game | Hộp hỏi | Tiết kiệm pin đang bật: "Mở cài đặt Tiết kiệm pin" hoặc "Chơi luôn". Sau "Chơi luôn", 24 giờ tới app không hỏi lại. |
| Đang chơi, máy nóng | Hộp báo, tự đóng sau 30 giây | "Máy đang nóng". Nút "Máy bố, bố biết" tắt mọi báo nóng (hộp này và thanh "Máy đang nóng lên") trong 24 giờ (`AgvnHeatAck`). |
| Đang chơi | Thanh trên game; game vẫn chạy bên dưới | Game chậm đều suốt 1 phút. Sau khi bấm một cách sửa, thanh hỏi "Mở lại game ngay" (nhớ lưu game trước) hay "Để lần sau". |
| Game vừa thoát hoặc bị lỗi | Hộp hỏi ở thư viện (hoặc Big Picture) | Game tắt ngay khi mở, crash, thiếu DirectX, thiếu file `.dll`; game "Chạy nhẹ" báo lỗi script, bị tắt đột ngột, bị treo. Bấm một cách sửa là game mở lại ngay. |
| Android đã tắt game | Hộp hỏi khi về thư viện (trong 24 giờ) | Máy hết RAM; HyperOS tắt game khi chuyển sang app khác. |
| Game Windows vừa hiện khung | Thanh trên game | Khung game nhỏ hơn màn hình: app phóng cho vừa luôn. Khung game lớn hơn màn hình: app đề nghị đổi màn hình game. Xem "Vừa màn hình" bên dưới. |
| Game Windows vẫn đen màn hình | Thanh trên game | Khoảng 20 giây sau khi mở (lâu hơn nếu game thường mở chậm), game vẫn chưa hiện hình, và có dấu hiệu màn hình chưa đủ lớn cho game (khổ game KiriKiri, game KiriKiri chưa đọc được khổ, khung game bị cắt, Wine từ chối độ phân giải game xin): app đề nghị màn hình lớn hơn. Game DirectX đen mà không có dấu hiệu đó: app đề nghị "Bật Đồng bộ khung hình". Xem "Màn hình đen" bên dưới. |

Mỗi lỗi chỉ hỏi một lần. Cách sửa đã thử mà vẫn lỗi thì lần sau không hiện lại, cho đến khi game chạy tốt một
lần. Lần chơi mà game đứng hình ít nhất 10 giây trước khi tắt, dù người chơi bấm ít nhất 5 lần, không tính là chạy tốt
(`AgvnEvidence.frozeAtEnd`): ngày 05/10, Party Me (Godot) trên máy Mali-G615 ba lần đứng hình 17–24 giây rồi tự tắt, và
bản 0.1.17 tính lần dài 88 giây là chạy tốt, nên quên các cách vẽ đã thử và đổi qua đổi lại. `su-kien.txt` ghi "Game
đứng hình … giây trước khi tắt". Thanh game chậm có nút "Để vậy, không hỏi lại" cho riêng game đó.

Thư viện và Big Picture đều hỏi, tùy màn hình nào đang mở: hộp hỏi chỉ hiện ở màn hình đang ở trên cùng.

## Lỗi app nhận ra (`assets/agvn/game-problems.json`)

### Game Windows

| Lỗi | App nhận ra nhờ | Cách sửa (theo thứ tự) |
|---|---|---|
| Game cần Steam | Wine báo thiếu `steam_api.dll` | Gửi nhật ký |
| Thiếu file `.dll` | Wine: `Library X.dll (which is needed by …) not found` | Bật thư viện Windows chứa file đó (DirectX, XAudio, VC++ 2010), gửi nhật ký |
| File `.dll` hỏng hoặc sai 32/64-bit | Wine: lỗi `c000007b` | Gửi nhật ký |
| Game cần .NET | Wine: `Wine Mono is not installed`: file chạy của game, hoặc chương trình nó mở, viết bằng .NET (C#) mà Wine chưa có Wine Mono. Xem "Game .NET (Wine Mono)" bên dưới | Cài .NET cho game (Wine Mono), gửi nhật ký. Hộp hỏi cũng gợi ý thêm lại game bằng file chạy chính nếu thư mục còn file `.exe` khác |
| Wine chưa chạy được phần .NET của game | Wine: `Could not load Mono into this process` hoặc `mscoree.dll not found, IL-only binary`: có Wine Mono rồi mà Wine không nạp được | Gửi nhật ký, kèm gợi ý file chạy như trên |
| Driver đồ họa bị lỗi khi game vẽ hình | Hộp "Assertion failed!" của Wine nhắc `winevulkan/loader_thunks.c` và một hàm Vulkan (`vkCreateShaderModule`...): driver bị lỗi ngay trong lúc game nhờ nó dựng hình | GPU Mali, lỗi lúc tạo shader: tắt bước sửa shader "hằng số", rồi bước "cắt hình". Đổi bản DXVK, đổi driver, dùng WineD3D. Game Godot: bỏ cách vẽ đã đổi, chạy bằng Direct3D 11 (ANGLE), hai bước sửa shader, đổi driver |
| Godot không mở được OpenGL | Câu lỗi của Godot (nhật ký `godot.log` hoặc hộp "Unable to initialize video driver"), hoặc game Godot tắt trước khi hiện hình | Chạy Godot bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), bằng Direct3D 11 (ANGLE, Godot 4.4 trở lên), bỏ cách vẽ đã đổi, đổi driver |
| Godot không mở được OpenGL lẫn Direct3D 11 | Hộp lỗi của Godot 4.4 trở lên: "...required OpenGL 3.3 or Direct3D 11 version" | Chạy bằng Vulkan, bỏ cách vẽ đã đổi, đổi driver |
| Godot không mở được Vulkan | Hộp lỗi của Godot: "...required Vulkan version" | Bỏ cách vẽ đã đổi, chạy bằng Direct3D 11 (ANGLE), đổi driver |
| Game Godot tự tắt sau khi đổi cách vẽ | App đã đổi cách vẽ của game, rồi game tự tắt trong một phút đầu mà không để lại câu lỗi nào | Chạy bằng Direct3D 11 (ANGLE), bỏ cách vẽ đã đổi, đổi driver |
| Game bị tắt khi vẽ hình (OpenGL qua Zink) | Zink báo `vkCreateGraphicsPipelines failed` rồi game tự tắt; hay gặp ở GPU Mali, vì Zink thiếu vài tính năng trên đó | Game Godot: chạy bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), bằng Direct3D 11 (ANGLE), bỏ cách vẽ đã đổi. Mọi game: đổi driver |
| Không tạo được DirectX / thiếu DirectX 11 | Câu lỗi của DXVK, Unity (`InitializeEngineGraphics failed`) | Đổi bản DXVK, đổi driver (Turnip ↔ System), dùng WineD3D |
| Driver đồ họa lỗi giữa chừng | `VK_ERROR_DEVICE_LOST` | Để Turnip tự chọn chế độ dựng hình, đổi driver, đổi DXVK, hạ Đồ họa |
| Hết bộ nhớ đồ họa / hết RAM | DXVK, Unity, Unreal báo hết bộ nhớ | Hạ Đồ họa. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ (`giam-ram.md`) |
| File game hỏng | Unity: `is corrupted` | Gửi nhật ký (cần chép lại game) |
| Không tìm thấy file của game | Hộp "File not found." của Wine: file chạy của game, hoặc file game cần, không còn ở chỗ cũ (thư mục game bị đổi tên, chuyển hay xoá) | Gửi nhật ký (cần chép lại thư mục game, thêm lại game) |
| File chạy của game không đúng định dạng | Hộp "Bad EXE format" của Wine: file được mở không phải chương trình Windows mà Wine chạy được (file hỏng hoặc chép chưa xong, chương trình 16-bit hay DOS, không phải file chạy của game). Trước đây app chỉ báo "Game tắt ngay sau khi mở" | Gửi nhật ký (cần chép lại game, hoặc thêm lại game bằng đúng file chạy) |
| Game crash | Wine in báo cáo crash | Đổi DXVK, giả lập CPU ổn định hơn, đổi driver, về cấu hình gốc |
| Game tự tắt lúc máy gần hết RAM | Game tự đóng, không crash, không câu lỗi, trong khi 30 giây trước đó RAM trống đã xuống dưới mức của thanh cảnh báo RAM (một phần mười RAM của máy, từ 600 MB đến 1,2 GB), kể cả khi game chưa kịp hiện hình | Dùng lại cấu hình đã chạy được, hạ Đồ họa, gửi nhật ký. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ. Game đã tiết kiệm như vậy mà vẫn hết RAM: không đề nghị hạ Đồ họa nữa |
| Game không chịu độ phân giải nhỏ ("The current resolution is too low") | Game tắt trước khi hiện hình, màn hình dưới 480 dòng | Nâng Đồ họa (thường là Thấp 854×480), dùng lại cấu hình đã chạy được |
| Game không chạy với cấu hình mới | Lần trước chạy được, đổi cấu hình xong thì tắt trước khi hiện hình | Dùng lại cấu hình đã chạy được, về cấu hình gốc |
| Game tắt ngay, không rõ lỗi | Game tắt trước khi hiện hình | Về cấu hình gốc, đổi DXVK, đổi driver, gửi nhật ký |

Khi máy hết RAM, hệ thống có thể tắt riêng tiến trình game mà không tắt AGVN. Lúc đó Wine kết thúc với mã 0, còn Wine,
DXVK và engine của game đều không kịp ghi gì. Trước đây app coi đó là game thoát bình thường và còn lưu cấu hình đó là
cấu hình chạy tốt. Ví dụ ngày 04/10: game Unity trên máy Mali-G610 7,2 GB bị tắt ở cùng một cảnh trong cả bốn lần
chơi, 7–17 giây sau thanh cảnh báo RAM, khi RAM trống chỉ còn 0,4–0,7 GB. Người chơi tự thoát từ menu của game lúc RAM
đang thấp cũng gặp thông báo này, nên trong thông báo có câu "Nếu chính bạn vừa thoát game thì bỏ qua thông báo này".

App xét lỗi theo thứ tự: lỗi có câu lỗi hay báo cáo crash của Wine trước, rồi tới hết RAM, rồi mới tới các lỗi chỉ đoán
từ cách game tắt (tắt trước khi hiện hình, màn hình nhỏ). Ngày 05/10, Lg Light (Lifeguard Holic, Unity 6) trên máy
Mali-G925 11,1 GB không qua được màn tải trong cả năm lần chơi: game lên tới 5,6–7,1 GB rồi bị tắt khi RAM trống chỉ
còn 170–370 MB. Bản 0.1.17 đọc hai lần trong đó thành "Game tắt ngay sau khi mở" (đề nghị về cấu hình gốc, đổi DXVK,
đổi driver) và "Game không chạy ở độ phân giải 640×360" (đề nghị nâng Đồ họa, và nhớ 640×360 là màn hình game không
chịu). Không cách nào trong đó bớt được RAM.

Game đã tiết kiệm RAM như Siêu nhẹ (vì từng hết RAM) mà vẫn hết RAM thì hộp hỏi nói rõ điều đó ("…dù game đã chạy với
mức tiết kiệm RAM cao nhất…", `low-ram-saved`, `killed-low-memory-saved`) và không đề nghị hạ Đồ họa. Lúc đó hạ Đồ họa
chỉ đổi màn hình và FPS, gần như không bớt RAM: Lg Light lên 5,8 GB ở 1600×900 và 6,8 GB ở 640×360. Còn lại là đóng
app khác, khởi động lại máy, dùng lại cấu hình đã chạy được (nếu có) và gửi nhật ký. Nếu vẫn vậy, game cần nhiều RAM
hơn máy còn trống.

App biết game Godot là bản nào (4.6, 3.5...) nhờ dòng đầu nhật ký của Godot (`godot.log`, app chép vào thư mục phiên
chơi), hoặc nhờ phần đầu file `.pck` của game (file `.pck` riêng hoặc gắn trong file `.exe`).

Game Godot đóng gói trong một file `.exe` (không có file `.pck` riêng) trước đây bị nhận là game không rõ engine, nên
thiếu OpenGL 3.3 qua Zink và không có cách sửa nào của Godot. Giờ app nhận ra nó lúc thêm game và lúc mở game (gói của
Godot nằm ở cuối file `.exe`), hoặc khi hộp lỗi của game là câu của Godot.

"Direct3D 11 (ANGLE)": Godot 4.4 trở lên vẽ được bằng ANGLE, tức OpenGL ES dựng trên Direct3D 11. Như vậy game đi qua
DXVK chứ không qua Zink, hợp với GPU Mali, nơi Zink thiếu tính năng. Nếu ANGLE không mở được, Godot tự quay về OpenGL
như cũ. App chỉ đổi cách vẽ bằng tham số chạy game (`--rendering-method gl_compatibility --rendering-driver
opengl3_angle`), và mỗi lần chỉ giữ một cách vẽ đã đổi.

DXVK-Sarek người chơi tự cài (nút "Cài thêm" ở mục DXVK) giờ cũng được tắt một bước sửa shader của wrapper
(`WRAPPER_NO_PATCH_OPCONSTCOMP`), như upstream vẫn làm với bản Sarek của họ. Bước này chạy đúng lúc game tạo shader
(`vkCreateShaderModule`). Một máy Mali-G925 dùng Sarek tự cài đã bị lỗi driver ở đúng bước đó.

Wrapper Vulkan đi kèm app (`libvulkan_wrapper.so`, hàm `wrapper_CreateShaderModule`) chỉ sửa shader khi GPU là Mali
(driver ARM). Nó chép shader ra rồi chạy hai bước: đổi `OpConstantComposite` thành `OpSpecConstantComposite`, và bỏ
`ClipDistance`/`CullDistance`. Mỗi bước có một công tắc (`WRAPPER_NO_PATCH_OPCONSTCOMP`,
`WRAPPER_NO_REMOVE_CLIP_DISTANCE`; số khác 0 là tắt). Khi game Mali bị "Assertion failed!" lúc tạo shader, hộp hỏi có
“Tắt bước sửa shader "hằng số" của GPU Mali” và “Tắt bước sửa shader "cắt hình" của GPU Mali” (`AgvnWrapperPasses`). Mỗi nút ghi công tắc vào
biến môi trường của riêng game đó. Nút đã thử mà vẫn lỗi thì lần sau hộp hỏi nút còn lại, nên hai bước được thử lần lượt.
Thư mục game dài thì dòng Wine ghi chữ trong hộp bị cắt giữa tên hàm (`vkCreateShaderMo`) hoặc trước tên hàm; hai
nút vẫn được hỏi (`AgvnWrapperPasses.creatingShader`).

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
| Máy hết RAM nên Android tắt game | Lý do Android ghi lại (`LOW_MEMORY`) | Hạ Đồ họa (game Windows và Ren'Py), gửi nhật ký. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ. Game đã tiết kiệm như vậy mà vẫn bị tắt: dùng lại cấu hình đã chạy được, gửi nhật ký, không đề nghị hạ Đồ họa nữa |
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
hiện thành khung nhỏ ở góc, hoặc bị cắt mất một phần. Mỗi giây, `AgvnScreenFit` tìm khung của game (`AgvnGameWindow`):
cửa sổ ứng dụng lớn nhất, không tính màn nền của Wine (`explorer.exe`). Wine chạy trong màn nền ảo, nên cửa sổ game
nằm bên trong cửa sổ màn nền; app tìm ở mọi tầng. Khi game đã chạy và khung đứng yên 3 giây, app xét
(`AgvnFitMath.verdict`):

| Khung game | App làm gì |
|---|---|
| Nhỏ hơn 85% màn hình cả chiều ngang lẫn chiều dọc, hoặc khung phóng lên lấp màn hình điện thoại thì to ra ít nhất 1/0,85 lần (khoảng 18%), như khung 1288×769 của game KiriKiri 1280×720 trên màn hình 1280×1024 (ngang đã kín, dọc chỉ ba phần tư) | Phóng khung game ra cả màn hình ngay, giữ đúng tỉ lệ (hoặc kéo giãn nếu game đang bật "Kéo giãn"). Chạm vào đâu trên khung là trúng chỗ đó trong game. Thanh báo có "Giữ như vậy" và "Trả lại như cũ". |
| Lớn hơn màn hình | Phần thừa bị cắt, chuột cũng không tới được, nên không phóng nhỏ được. Thanh báo đề nghị "Đổi màn hình game thành <cỡ khung>", rồi "Mở lại game ngay". Cửa sổ phóng to thì viền của nó thò ra ngoài mỗi cạnh vài điểm ảnh (Wine: 6), hình game bên trong vẫn đủ: thò ra không quá 16 điểm ảnh mỗi cạnh thì không tính là bị cắt (`AgvnFitMath.cutOff`). Khung game lớn theo màn hình thì đổi màn hình không giúp được: xem "Khung game lớn theo màn hình" bên dưới. |
| Vừa | Không làm gì. Khung đổi cỡ sau đó thì app xét lại. |

- Nút ⛶ trên thanh ⌨ ✎ 👁 bật hoặc tắt "Vừa màn hình" bất cứ lúc nào. Khung lớn hơn màn hình thì ⛶ mở thanh đổi màn
  hình.
- Lựa chọn lưu theo game (`agvnFit` trong lối tắt: 1 bật, 0 tắt và không hỏi lại), nên lần sau game mở là vừa luôn.
- Chỉ trình vẽ Vulkan (mặc định của AGVN) phóng được khung. Game chọn EGL hoặc DisplayX thì khung nhỏ cũng được đề nghị
  đổi màn hình.
- Khung game không bao giờ là cửa sổ của chương trình mở game: `explorer.exe`, `winhandler.exe`, `start.exe`. Ngày 05/10,
  hộp "File not found." 144×106 của `winhandler.exe` (file chạy của Monster Black Market không còn ở chỗ cũ) bị phóng ra
  cả màn hình như khung game.

### Khung game lớn theo màn hình

Có game mà khung luôn lớn hơn màn hình một chút, dù màn hình là bao nhiêu. Party Me trên máy Mali-G615 (05/10, bản
0.1.17): khung thò ra 12 điểm ảnh mỗi chiều, nên mỗi lần người chơi bấm "Đổi màn hình game thành …", màn hình lớn thêm
12 điểm ảnh (1080×664, 1092×676, 1104×688 chỉ trong một buổi). Bản 0.1.14 đoán chỗ đặt khung (-6, -6) để bỏ qua, nhưng
đoán sai, nên vòng lặp vẫn còn.

Nay khi đổi màn hình cho khung game (`AgvnScreenGrowth`), app ghi lại màn hình trước lần đổi đầu tiên (`agvnFitFrom`),
màn hình app đặt (`agvnFitTo`) và khung thò ra bao nhiêu (`agvnFitPast`). Lần mở sau, nếu khung lại thò ra đúng chừng đó
(chênh không quá 2 điểm ảnh) trên màn hình app đặt, thì khung lớn theo màn hình. Khi đó app không đề nghị nữa, trả game
về màn hình cũ từ lần mở sau (`agvnFit`: 0), và báo một lần "Khung game lớn theo màn hình". Cách này đúng với mọi game,
mọi máy, khung đặt ở đâu cũng được. Game có khung cỡ riêng (như Monster Black Market, 1288×746 trên 1280×720) thì vừa
màn hình mới, nên không bị trả về.

## Màn hình đen (game Windows)

Game KiriKiri trên màn hình không lớn hơn khổ game (bằng cũng tính) đứng ở màn hình đen: KAG3 chuyển sang toàn màn
hình, và toàn màn hình DirectDraw treo đen trong Wine (xem phần KiriKiri bên dưới). Game khác xin độ phân giải lớn hơn
màn hình thì Wine trả lỗi -2, và game không vẽ gì nữa. `AgvnBlackScreen` canh chuyện này cho mọi game Windows:

- Mỗi 2 giây, app đọc 5 hàng điểm ảnh của khung game (`AgvnGameWindow`). Game vẽ bằng Vulkan hoặc OpenGL (DXVK, Zink)
  thì app đọc hình cuối cùng game gửi lên. Game vẽ bằng GDI thì app đọc hình trong khung. Hình nào máy không cho đọc thì
  không tính, nên không báo nhầm.
- Hàng nào cũng chỉ có điểm ảnh tối (đỏ, xanh lá, xanh dương đều ≤ 24) thì là màn hình đen.
- Khung toàn một màu sáng (mọi điểm đọc cùng một màu, chênh không quá 12) chưa phải hình của game: đó là nền của cửa
  sổ lúc game chưa vẽ. Ngày 06/10 (bản 0.1.20), cửa sổ xám của một game KiriKiri bị tính là "đã hiện hình", nên khi
  game treo đen lúc vào toàn màn hình, 5 phút liền app không hỏi.
- App hỏi khi màn hình đen 3 lần đọc liền nhau (6 giây), sau 20 giây kể từ lúc mở game. Game thường mở chậm thì thời
  gian chờ là lần mở trước cộng 10 giây (`AgvnStartTimes`). App chỉ canh trong 1 phút sau thời gian chờ.
- RAM của app và game còn tăng nhanh (từ 64 MB trở lên giữa hai lần đo RAM, 5 giây một lần) thì game đang tải: lần đọc
  đen lúc đó không tính, và 1 phút canh tính từ lúc game tải xong (`AgvnSessionTrack.grewMb`). Ví dụ ngày 05/10: Lg Light
  (Unity) trên máy Mali-G925 đen trong lúc RAM tăng 150–300 MB mỗi 10 giây, rồi bị tắt vì hết RAM. Bản 0.1.17 đề nghị
  nó màn hình 1920×1080 lúc 22 giây, mà màn hình lớn hơn chỉ tốn RAM hơn.
- Game đã hiện hình rồi mới đen (cảnh mờ dần, cảnh tối) thì app không hỏi. Game đã hiện một hộp thông báo
  (`trace:msgbox`) cũng vậy: game đang chờ người chơi bấm, hoặc đã lỗi ("Assertion failed!", "Tự sửa lỗi" đọc hộp này
  khi game tắt). Trừ khi Wine đã từ chối độ phân giải game xin: nhật ký Wine có dòng `display settings returned -2`. Khi
  đó app hỏi cả khi chưa thấy khung game. Dòng này và dòng hộp thông báo được nhớ suốt lần chơi, dù đã trôi khỏi 300
  dòng cuối của Wine.
- Không dùng `display mode not found` hay `NtUserEnumDisplaySettings Failed to query` làm dấu hiệu: hai dòng này (cùng
  `wined3d_output_get_mode Invalid mode_idx N`) là lúc game đọc hết danh sách độ phân giải, game DirectDraw/Direct3D nào
  cũng có. Số `index 0` trong dòng là phần còn lại sau khi đếm, không phải "chế độ đầu tiên".
- Màn hình đề nghị (`AgvnBlackScreenRules.bigger`): màn hình lớn hơn khổ game KiriKiri nếu biết khổ
  (`AgvnKirikiriScreen.larger`); khung game nếu khung lớn hơn màn hình; game KiriKiri chưa đọc được khổ (`-`): game đã
  vào toàn màn hình nên không nhỏ hơn màn hình, app đề nghị màn hình lớn hơn chính màn hình đang dùng (1280×720 thành
  1280×1024); nếu không thì cỡ kế tiếp trong 1280×720, 1600×900, 1920×1080. Màn hình đã là 1920×1080 thì app chỉ ghi
  vào nhật ký.
- Cỡ kế tiếp chỉ được đề nghị khi Wine đã từ chối độ phân giải game xin. Game đen mà không có dấu hiệu nào ở trên là
  game đang tải, hoặc khung hình của game không lên được màn hình. Màn hình lớn hơn không chữa được cả hai, nên app
  không hỏi, chỉ ghi vào nhật ký "… khung game <cỡ>: không hỏi".
  - Ví dụ ngày 05/10: Legend Cleaner (Unreal) trên máy Mali-G610 đen màn hình vì đang tải, rồi bị tắt vì hết RAM. Khung
    game là 711×400 trên màn hình 854×480, 1066×600 trên 1280×720 và 1440×720 trên 1600×900. Người chơi bấm đổi màn
    hình hai lần mà game vẫn đen.
  - Cũng ngày 05/10: Support Pregnancy School (Unreal) trên máy Mali-G615 (bản 0.1.17) đen cả màn hình 1280×720 sau 32
    giây, và thanh này đề nghị 1600×900. Game vào được khi người chơi bật "Đồng bộ khung hình" và "Tắt Present Wait",
    không nhờ màn hình lớn hơn.
- Game DirectX (DXVK) đen mà không có màn hình nào để đề nghị, và chưa bật cả hai mục trên: thanh đề nghị "Bật Đồng bộ
  khung hình" (`AgvnPresentSync`). Bấm thì cài đặt đồ họa của game có `syncFrame=1` và `disablePresentWait=1`, rồi "Mở
  lại game ngay". Người chơi tắt lại được trong cài đặt game. Bản 0.1.18 tự bật hai mục này cho mọi game DirectX trên
  máy dưới 9 GB RAM không dùng Turnip, nên cả game vẫn chạy tốt cũng mất khung hình. Bản sau chỉ đề nghị khi game đen.
- Khung game lớn theo màn hình (xem trên) thì không được đề nghị màn hình cỡ khung nữa.

Thanh "Màn hình game vẫn đen" có ba nút: "Đổi màn hình game thành <cỡ>" hoặc "Bật Đồng bộ khung hình" (rồi "Mở lại game
ngay"), "Chờ thêm" (không hỏi lại trong lần chơi này), và "Để vậy, không hỏi lại" (`agvnBlackScreen`: 0 trong lối tắt).
Game đã chọn "Để vậy, không hỏi lại" vẫn được canh: `su-kien.txt` vẫn ghi "Màn hình đen sau … giây …", chỉ không hiện
thanh. Hết thời gian canh mà khung vẫn đen và app không hỏi (game đã hiện hình trước đó, hoặc đã hiện hộp thông báo),
`su-kien.txt` ghi "Màn hình đen tới giây …, khung game …: không hỏi, <lý do>". `tom-tat.txt` của phiên chơi ghi "Màn hình
đen sau … giây".

## Game KiriKiri: màn hình lớn hơn khổ game

Khổ game KiriKiri nằm trong `system/Config.tjs` (`;scWidth = 1280;`, `;scHeight = 720;`). `AgvnKirikiri` đọc file này ở
đúng chỗ game đọc: gói vá mới nhất (`patch3.xp3`, `patch2.xp3`, `patch.xp3`), `data.xp3`, thư mục `data` chưa đóng gói,
rồi các gói `.xp3` khác (gói nhỏ trước, tối đa 6 gói). `AgvnXp3` đọc gói XP3 theo cách của krkr2: chỉ mục thường hoặc
nén zlib, bản 2 có "cushion", file nén hoặc không. `AgvnTjsText` đọc chữ: UTF-16, UTF-8, Shift-JIS, và dạng riêng của
KiriKiri (`FE FE 0/1/2 FF FE`). Game mã hóa gói thì app không đọc được khổ game, và mọi thứ giữ như cũ.

KAG3 vào toàn màn hình ngay khi mở nếu màn hình **không lớn hơn** khổ game, kể cả khi bằng (`system/MainWindow.tjs`:
`if(System.screenWidth <= scWidth && System.screenHeight <= scHeight)`). Toàn màn hình DirectDraw (chế độ độc quyền)
treo đen trong Wine: ngày 06/10 (bản 0.1.20), game 1280×720 trên màn hình 1280×720 treo ở `ddraw_set_cooperative_level`
(`DDSCL_ALLOWREBOOT`), tiến trình game ngủ, CPU không chạy. Cùng game trên màn hình 1280×1024 chạy trong cửa sổ, vẽ
bình thường. Wine không thiếu độ phân giải: trong màn nền ảo, Proton 9 liệt kê 9 cỡ cho mỗi độ màu, có cả cỡ màn hình
(`desktop_update_display_devices`); game vẫn treo khi vào toàn màn hình. Chỗ treo nằm trong Wine, cần nhật ký
`+ddraw,+d3d,+win,+x11drv` để tìm. Trong lúc chờ, game KiriKiri luôn được màn hình lớn hơn nó (`AgvnKirikiriScreen`):

- Màn hình đang dùng không nhỏ hơn khổ game ở chiều nào và lớn hơn ở ít nhất một chiều: giữ nguyên.
- Nếu không: cỡ đầu tiên trong 640×480, 800×600, 1024×768, 1280×1024, 1600×1200, 1920×1440, 2560×1920 có chiều ngang
  không nhỏ hơn game và chiều dọc hơn game ít nhất 64 điểm ảnh (chỗ cho thanh tiêu đề, thanh menu và viền cửa sổ). Ví
  dụ: game 1280×720 hoặc 1280×960 → 1280×1024; 800×600 → 1024×768; 1024×768 → 1280×1024; 1600×900 → 1600×1200.
- Áp dụng khi thêm game (hộp xem trước ghi "Độ phân giải: 1280x1024 (lớn hơn khổ game, để game chạy trong cửa sổ,
  không treo màn đen)"), khi đổi mức "Đồ họa" (và cách sửa "Đổi Đồ họa sang …"), và mỗi lần mở game: lối tắt cũ đang để
  màn hình bằng khổ game được nâng lên và lưu lại (`adb logcat -s AGVN`: `KiriKiri game 1280x720: screen 1280x720 ->
  1280x1024`).
- Game chạy trong cửa sổ nên khung nhỏ hơn màn hình: "Vừa màn hình" phóng khung lên cả màn hình điện thoại (xem trên).
- Khổ game lưu trong lối tắt (`agvnGameSize`; `-` nếu không đọc được). Game KiriKiri đã thêm từ trước thì app đọc khổ ở
  lần mở đầu tiên (màn hình được nâng từ lần mở sau). Thanh "Màn hình đen" dùng khổ này để đề nghị.
- Trong game, bấm F4 hoặc mục "Toàn màn hình" thì game vẫn treo như trên, cho tới khi Wine được sửa.

## Phim trong game không phát được (game Windows)

Ngày 06/10 (bản 0.1.20), một game KiriKiri đứng chờ phim mở đầu (WMV3, tức Windows Media Video 9, 1280×720 trong
ASF) 4 phút 30 giây. Wine của app (`proton-9.0-arm64ec`) đưa phim cho bộ giải mã của GStreamer (`avdec_wmv3`) mà thiếu
phần đầu chuỗi (`codec_data`, nằm sau `VIDEOINFOHEADER` hoặc trong `MF_MT_USER_DATA`). Bộ giải mã từ chối mọi khung:
`Subclass refused caps` và `Failed to push transform input, error -4` lặp hàng trăm lần mỗi giây. Wine không báo lỗi
cho game (không có `EC_ERRORABORT` hay `EC_COMPLETE`), nên game chờ mãi. Lỗi nằm trong winegstreamer của bản Wine này
(bản dựng trên Termux, có `wg_media_type.c`); sửa tận gốc cần dựng lại Wine hoặc đổi sang bản Wine có bản sửa.

Trong lúc chờ, app canh lỗi này cho mọi game (`AgvnMovieWatch`, `AgvnMovieRules`):

- Tắt "Bật debug Wine" thì GStreamer không in gì (winegstreamer chỉ đặt `GST_DEBUG` khi kênh gstreamer của Wine bật).
  App đặt `GST_DEBUG=WINE:2,videodecoder:2`: chỉ cảnh báo của bộ giải mã phim và của winegstreamer. Người chơi tự đặt
  `GST_DEBUG` trong biến môi trường thì app giữ của người chơi.
- Từ 100 dòng từ chối trở lên trong 5 giây, kéo dài ít nhất 1 giây: phim không phát được. `su-kien.txt` ghi "Phim
  trong game không phát được: WMV3 1280x720 …" (định dạng đọc từ dòng caps Wine in ra), và dòng trên góc phải đổi
  thành "Phim trong game không phát được (0:12)".
- Thanh "Phim trong game không phát được" có "Bỏ qua phim" và "Chờ thêm". "Bỏ qua phim" nhấp chuột giữa khung game;
  2,5 giây sau phim vẫn kẹt thì gửi phím Esc. `su-kien.txt` ghi "Bỏ qua phim: được, bằng nhấp chuột" (hoặc "phím Esc"),
  hoặc "Bỏ qua phim: không được". Không được thì lối tắt nhớ (`agvnMovieSkip`: 0), lần sau thanh chỉ báo lỗi, không
  đề nghị bỏ qua nữa.
- Phim ngừng lỗi 10 giây rồi một phim khác lỗi: app báo lại.

Nhật ký Wine không còn phình vì dòng lặp (`AgvnLogRepeats`): dòng nào đã ghi trong 5 giây qua (bỏ qua giờ, luồng và
địa chỉ `0x…`) thì không ghi lại, mỗi giây ghi một dòng "[AGVN] ×N nữa: …". Quá 64 MB thì ngừng ghi, kèm một dòng báo.
300 dòng cuối của Wine (`wine-cuoi.txt`, và chỗ "Tự sửa lỗi" đọc) cũng gộp dòng lặp ("[AGVN] ×N dòng lặp lại"), nên
phim lỗi không đẩy các dòng khác ra ngoài.

## Game .NET (Wine Mono)

Game hay trình mở game viết bằng .NET (C#) cần Wine Mono, bộ chạy .NET của Wine. Proton 9 đi kèm app không có sẵn bộ
này, nên trước đây Wine đóng các chương trình đó ngay khi mở (`Wine Mono is not installed`). Ví dụ ngày 05/10:
"Yarisutemesubuta Cheat02" trên máy Adreno 610 mở `YARISUTEMESUBUTA + AGVN.exe`, một chương trình .NET, và tắt ngay
cả ba lần.

- App kèm sẵn Wine Mono 9.3.1 (`AgvnWineMono`), đúng bản `mscoree.dll` của Proton 9 tìm. Đó là file gốc của Wine
  Mono, `wine-mono-9.3.1-x86.tar.xz` (44 MB): bản dựng app tải nó từ GitHub và kiểm SHA-256 theo
  `scripts/agvn/pins.txt`.
- App chỉ giải nén (235 MB) cho game cần nó:
  - lúc mở game, nếu file chạy là chương trình .NET (đầu file `.exe` có phần CLR);
  - hoặc khi người chơi bấm "Cài .NET cho game (Wine Mono…)" trong hộp "Game cần .NET", dành cho trình mở game
    (không phải .NET) mở tiếp một chương trình .NET. Nút này lưu `agvnWineMono` = 1 trong lối tắt, và lần mở sau app
    giải nén.

  Game khác không tốn chỗ hay thời gian. Lúc giải nén, màn hình khởi động ghi tiến độ, ví dụ "Đang cài .NET cho game,
  chỉ lần đầu: 40%".
- Wine Mono nằm trong thư mục của Wine: `imagefs/opt/proton-9.0-arm64ec/share/wine/mono/wine-mono-9.3.1`. Đây đúng là
  chỗ bản dựng Proton của Valve đặt nó, và `mscoree.dll` tìm ở đó qua `WINEDATADIR`. Mọi game dùng Wine đó đều thấy,
  nên chỉ giải nén một lần. Nếu app cài lại phần hệ thống (thư mục `opt` bị xoá), lần mở game .NET sau giải nén lại.
- App chỉ cài khi `mscoree.dll` của Wine game đang dùng tìm đúng bản 9.3.1. Wine khác (tự cài thêm) cần bản khác thì
  app không cài, và `su-kien.txt` ghi bản Wine đó cần.
- App giải nén vào một thư mục tạm rồi mới đổi tên, nên lần giải nén dở (máy hết chỗ) không bị coi là đã cài. Máy còn
  dưới 288 MB trống thì app không giải nén, và báo cần bao nhiêu chỗ.
- `su-kien.txt` của phiên chơi ghi "Đã cài Wine Mono 9.3.1 cho game cần .NET (… s)" hoặc "Game cần .NET: đã có Wine
  Mono 9.3.1".
- Chương trình .NET 32-bit (thường gặp nhất, kể cả "AnyCPU" ưu tiên 32-bit) chạy qua FEX bằng `libmono-2.0-x86.dll`.
  Chương trình .NET 64-bit, hoặc "AnyCPU" không ưu tiên 32-bit, chạy dạng 64-bit và cần `libmono-2.0-x86_64.dll`
  (mã x86-64, chạy giả lập). Cả hai chưa thử trên máy thật. Wine không nạp được thì hộp hỏi là "Wine chưa chạy được
  phần .NET của game".

## Nhật ký Wine luôn có dòng lỗi

Khi tắt "Bật debug Wine", app vẫn cho Wine in ba nhóm lỗi: `err+module` (thiếu hoặc hỏng file `.dll`), `err+mscoree`
(.NET) và `err+system` (Wine từ chối độ phân giải game xin: `Changing ... display settings returned -2`). Các nhóm này
chỉ in khi có lỗi thật, nên không làm game chậm.

App luôn đặt `EmulateModelist` và `EmulateModeset` = `Y` (khóa `Software\Wine\X11 Driver`), dù bản Wine có XRandR hay
không. Lưu ý: Proton 9 đi kèm app không đọc hai khóa này (trong `winex11.so` không có tên khóa nào). Trong màn nền ảo,
danh sách độ phân giải của nó có các cỡ chuẩn không lớn hơn màn hình game, cộng cỡ màn hình (`win32u`,
`desktop_update_display_devices`). Game xin cỡ lớn hơn thì nhận lỗi -2. Vì vậy màn hình game phải đủ lớn ngay từ đầu
(phần KiriKiri và "Màn hình đen" ở trên). `adb logcat` ghi `Wine mode emulation keys written (a Wine may ignore them)`.

Bật hay tắt debug, Wine cũng ghi chữ trong mọi hộp thông báo game hiện ra (`trace+msgbox`): mỗi hộp một dòng
`trace:msgbox:MSGBOX_OnInit L"..."` trong `wine-cuoi.txt`. Wine cắt dòng này sau khoảng 290 ký tự. Nhiều game chỉ báo lỗi
bằng hộp, ví dụ "Unable to initialize video driver" của Godot, hay "Assertion failed!" khi driver Vulkan bị lỗi.

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
- [ ] Game Godot báo "Unable to initialize video driver": hộp hỏi có "Cho game Godot chạy bằng Vulkan"; `wine-cuoi.txt` có
  dòng `trace:msgbox:MSGBOX_OnInit` với chữ trong hộp.
- [ ] Máy Mali, game Godot 4.4 trở lên (như Party Me) đã đổi sang Vulkan mà tự tắt trong một phút đầu: thư viện hỏi
  "Game Godot tự tắt sau khi đổi cách vẽ" với "Cho game Godot chạy bằng Direct3D 11 (ANGLE)". Bấm vào thì game mở lại,
  và `godot.log` của phiên đó nhắc ANGLE.
- [ ] Game hiện hộp "Assertion failed!" nhắc `vkCreateShaderModule`, bấm OK: thư viện hỏi "Driver đồ họa bị lỗi khi game
  vẽ hình" với nút đổi DXVK. Trên GPU Mali, hai nút đầu là “Tắt bước sửa shader "hằng số"…” và “…"cắt hình"…”;
  bấm một nút thì `moi-truong.txt` của lần chạy sau có công tắc đó bằng 1.
- [ ] Game Godot chỉ có một file `.exe`, không có `.pck`: thêm mới thì game được nhận là Godot ngay. Game đã thêm từ
  trước thì lần mở đầu `adb logcat -s AGVN` có dòng `... is a Godot game`. Cả hai trường hợp, `moi-truong.txt` có
  `MESA_GL_VERSION_OVERRIDE=3.3`.
- [ ] Game dùng DXVK-Sarek tự cài: `adb logcat` có dòng `Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass`.
- [ ] Máy GPU Mali, game Godot bị tắt sau dòng Zink `vkCreateGraphicsPipelines failed` trong `wine-cuoi.txt`: thư viện
      hỏi "Game Godot bị tắt khi vẽ hình". Nút là "Cho game Godot chạy bằng Vulkan" nếu game là Godot 4, hoặc "…GLES2" nếu
      game là Godot 3. Thư mục phiên chơi có `godot.log`.
- [ ] Bật Tiết kiệm pin rồi mở game (Windows và "Chạy nhẹ"): có hộp hỏi trước khi vào game. "Chơi luôn" vào game,
      không có thêm thông báo. Thoát rồi mở lại game trong 24 giờ: không hỏi nữa.
- [ ] Máy báo nóng ngay khi mở game (như Xiaomi 2306EPN60G của một người chơi: Android báo trạng thái nhiệt "nóng" khi
      pin mới 37 °C): hộp "Máy đang nóng" có nút "Máy bố, bố biết". Bấm nút, thoát rồi mở lại game: 24 giờ tới
      không còn hộp hay thanh báo nóng; `su-kien.txt` có "Người chơi chọn "Máy bố, bố biết"…".
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
- [ ] Game có khung lớn theo màn hình (như Party Me trên máy Mali-G615): bấm "Đổi màn hình game thành …" một lần rồi
      "Mở lại game ngay". Lần mở sau có thanh "Khung game lớn theo màn hình", không có thanh đổi màn hình nữa, và lần mở
      sau nữa game về lại màn hình cũ. Game đã bị đổi màn hình nhiều lần (như 1116×700): chọn lại mức "Đồ họa" để về cỡ
      của mức đó.
- [ ] Đổi tên thư mục của một game Windows rồi mở game: thư viện hỏi "Không tìm thấy file của game"; không có khung nhỏ
      nào bị phóng ra cả màn hình.
- [ ] Đang chơi một game Windows, vuốt AGVN khỏi danh sách app gần đây rồi mở lại app: không có hộp hỏi; `tom-tat.txt`
      của phiên đó ghi "Người chơi vuốt tắt AGVN…".
- [ ] Game Windows: "✕ Thoát" trên thanh ⌨ ✎ 👁 ⛶ hiện thanh "Thoát game?"; "Chơi tiếp" để game chạy tiếp, "Thoát game"
      đưa về thư viện và không có hộp hỏi.
- [ ] Game mở chậm (hơn 10 giây không vẽ, ví dụ game Unity trên máy yếu): lần mở sau, dòng trên góc phải ghi "Đang khởi
      động 0:25 · lần trước 1:40".
- [ ] Thêm một game KiriKiri 1280×720 (máy nào cũng vậy): hộp xem trước ghi "Độ phân giải: 1280x1024 (lớn hơn khổ game,
      …)"; `adb logcat -s AGVN` có `KiriKiri game size 1280x720 (data.xp3>system/Config.tjs)`. Vào game: không có dòng
      `DDSCL_ALLOWREBOOT` trong nhật ký Wine, plugin nạp ở `first.ks` có trong nhật ký, game vẽ hình trong cửa sổ, và
      vài giây sau khung game được phóng vừa màn hình điện thoại (thanh "Khung game nhỏ hơn màn hình"), không còn viền
      đen lớn trên dưới. Mở hộp Đồ họa: các mức đều ghi 1280×1024.
- [ ] Game KiriKiri đã thêm từ trước, lối tắt đang để màn hình bằng khổ game (1280×720): lần mở sau `adb logcat -s AGVN`
      có `KiriKiri game 1280x720: screen 1280x720 -> 1280x1024`, cài đặt game ghi 1280x1024, game vào được.
- [ ] Game KiriKiri chưa đọc được khổ (gói mã hóa, `agvnGameSize` = `-`) mà treo đen lúc mở: khoảng 20–30 giây sau có
      thanh "Màn hình game vẫn đen" với "Đổi màn hình game thành 1280×1024" (màn hình đang 1280×720); `su-kien.txt` có
      "Màn hình đen sau … giây …: hỏi đổi sang 1280x1024". Bấm rồi "Mở lại game ngay": game hiện hình.
- [ ] Game đã chọn "Để vậy, không hỏi lại" ở thanh màn hình đen mà vẫn đen: không có thanh, nhưng `su-kien.txt` vẫn có
      dòng "Màn hình đen sau … giây".
- [ ] Game KiriKiri phát phim WMV3 (như game ở phiếu 06/10, màn hình 1280×1024), "Bật debug Wine" tắt: trong 10 giây có
      thanh "Phim trong game không phát được" với "Bỏ qua phim"; dòng góc phải ghi "Phim trong game không phát được
      (…)"; `su-kien.txt` có "Phim trong game không phát được: WMV3 1280x720". Bấm "Bỏ qua phim": `su-kien.txt` có "Bỏ
      qua phim: được, …" và game đi tiếp, hoặc "Bỏ qua phim: không được"; khi đó lần mở sau thanh không còn nút bỏ qua.
- [ ] Bật "Bật debug Wine", để phim lỗi chạy 5 phút: file nhật ký Wine dưới vài MB, có các dòng "[AGVN] ×… nữa: …"
      thay cho hàng trăm nghìn dòng lặp; `wine-cuoi.txt` có "[AGVN] ×… dòng lặp lại" và vẫn còn các dòng khác.
- [ ] Game Unity phát phim H.264/MP4 qua Media Foundation: phim vẫn phát như trước, không có thanh phim lỗi;
      `moi-truong.txt` có `GST_DEBUG=WINE:2,videodecoder:2` khi tắt debug.
- [ ] Một game Unity/DXVK và một game Ren'Py vẫn vào menu như trước, không có thanh mới.
- [ ] Game có cảnh tối hoặc mờ dần sau khi đã hiện hình: không có thanh "Màn hình game vẫn đen".
- [ ] Máy Mali, game bị hộp "Assertion failed!" ngay khi mở (như Support Pregnancy School): để hộp đó hơn 30 giây,
  không có thanh "Màn hình game vẫn đen". Bấm OK thì thư viện hỏi "Driver đồ họa bị lỗi khi game vẽ hình".
- [ ] Game nặng trên máy RAM ít bị tắt giữa chừng ngay sau thanh "Game đang dùng quá nhiều RAM" (hoặc giả lập: mở
  nhiều app nặng rồi vào game): thư viện hỏi "Game tự tắt lúc máy gần hết RAM" với số MB còn trống; `su-kien.txt` có
  `Tự sửa lỗi: low-ram-end`. Chơi đủ RAM rồi thoát từ menu của game: không có hộp nào.
- [ ] Game mở cửa sổ nhỏ hơn màn hình và đen lâu lúc đang tải (như Legend Cleaner trên máy Mali): không có thanh "Màn
  hình game vẫn đen"; `su-kien.txt` có "Màn hình đen sau … giây, màn hình …, khung game …: không hỏi".
- [ ] Game DirectX đen cả màn hình (như Support Pregnancy School trên máy Mali-G615, cài đặt mặc định): khoảng 20 giây
  sau có thanh "Màn hình game vẫn đen" với "Bật Đồng bộ khung hình". Bấm rồi "Mở lại game ngay": `moi-truong.txt` có
  `MESA_VK_WSI_DEBUG=forcesync` và `WRAPPER_DISABLE_PRESENT_WAIT=1`, và game vào được như khi người chơi tự bật hai mục
  đó. Game DirectX hiện hình bình thường: không có hai biến này, trên mọi máy.
- [ ] Game đen cả màn hình mà Wine không từ chối độ phân giải: không có thanh đề nghị màn hình lớn hơn.
- [ ] Vuốt tắt AGVN khỏi danh sách app gần đây khi game Windows đang chạy, mở lại rồi "Gửi nhật ký": phiên đó có
  `wine-cuoi.txt`.
- [ ] Máy Mali dưới 9 GB RAM, game Unity hoặc Unreal chưa từng hết RAM, ở mức Cao: `su-kien.txt` không có "Tiết kiệm RAM
  như Siêu nhẹ", `moi-truong.txt` không có `dxvk.trackPipelineLifetime=True` (giống POCO).
- [ ] Game đứng hình rồi tự tắt (bấm mãi mà hình không đổi): `su-kien.txt` có "Game đứng hình … giây trước khi tắt";
  game Godot đã đổi cách vẽ thì lần sau không bị đổi qua đổi lại giữa các cách đã thử.
- [ ] Game vừa được hỏi "Game tự tắt lúc máy gần hết RAM": lần mở sau, `su-kien.txt` có "Tiết kiệm RAM như Siêu nhẹ: game
  từng bị tắt vì hết RAM trên máy này".
- [ ] Game hết RAM ngay lúc đang tải (màn hình đen, RAM tăng tới khi game bị tắt, như Lg Light trên máy Mali-G925): thư
  viện hỏi "Game tự tắt lúc máy gần hết RAM", không hỏi "Game tắt ngay sau khi mở" hay "Game không chạy ở độ phân giải
  …", kể cả ở Siêu nhẹ (640×360).
- [ ] Mở lại game đó (giờ đã "Tiết kiệm RAM như Siêu nhẹ") để nó hết RAM lần nữa: hộp hỏi ghi "…dù game đã chạy với mức
  tiết kiệm RAM cao nhất…" và không có nút hạ Đồ họa; `su-kien.txt` có `Tự sửa lỗi: low-ram-saved`.
- [ ] Game đen lâu lúc đang tải mà RAM còn tăng (như Lg Light): không có thanh "Màn hình game vẫn đen" khi RAM còn tăng;
  game tải xong mà vẫn đen thì thanh hiện sau khoảng 6 giây.
- [ ] Game .NET (như Yarisutemesubuta Cheat02): lần mở đầu, màn hình khởi động ghi "Đang cài .NET cho game, chỉ lần
      đầu: …%", rồi game mở. `su-kien.txt` có "Đã cài Wine Mono 9.3.1 cho game cần .NET". Lần mở sau không cài lại và
      ghi "đã có Wine Mono 9.3.1". `imagefs/opt/proton-9.0-arm64ec/share/wine/mono/wine-mono-9.3.1/bin` có hai file
      `libmono-2.0-x86.dll` và `libmono-2.0-x86_64.dll`.
- [ ] Mở một game không phải .NET: không cài gì, `su-kien.txt` không nhắc Wine Mono.
- [ ] Game có hộp "Bad EXE format" của Wine (ví dụ chọn nhầm một file không phải chương trình Windows, đổi đuôi
      thành `.exe`): thư viện hỏi "File chạy của game không đúng định dạng", không còn "Game tắt ngay sau khi mở".
- [ ] `adb logcat`: dòng `Wine mode emulation keys written`; `wine-cuoi.txt` của game bị đen vì Wine từ chối độ phân
      giải có `display settings returned -2`.
- [ ] `adb logcat -s AGVN`: dòng `doctor: <lỗi> for <game>` và `fix <cách sửa> -> <giá trị>`.
