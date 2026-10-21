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
| Bất cứ lúc nào, người chơi thấy lỗi | Nút "Tự sửa lỗi" trong menu ⋮ của game ở thư viện, nút "🩺 Tự sửa" trên thanh trong game | Hình chập chờn, đốm đen, chữ lỗi, không có tiếng: những lỗi app không tự thấy được. Người chơi chọn lỗi, app thử lần lượt từng cách. Xem "Người chơi báo lỗi" bên dưới. |
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
| Driver đồ họa bị lỗi khi game vẽ hình | Hộp "Assertion failed!" của Wine nhắc `winevulkan/loader_thunks.c` và một hàm Vulkan (`vkCreateShaderModule`...): driver bị lỗi ngay trong lúc game nhờ nó dựng hình | GPU Mali, lỗi lúc tạo shader: tắt bước sửa shader "hằng số", rồi bước "cắt hình". Đổi bản DXVK, đổi driver, chạy bằng Wine cũ (Proton 9) khi game đang chạy Proton 10, dùng WineD3D. Game Godot: bỏ cách vẽ đã đổi, chạy bằng Direct3D 11 (ANGLE), hai bước sửa shader, đổi driver |
| Game cần DirectX 11 mức 11.0 | Hộp của game Unreal: "A D3D11-compatible GPU (Feature Level 11.0, Shader Model 5.0) is required to run the engine.", hay câu đó bằng tiếng khác (game Nhật: "D3D11 互換の GPU (機能レベル 11.0、…) が…必要です", từ bản 0.1.35): DXVK hay WineD3D, với driver đang chọn, chưa tới mức DirectX 11 game cần (thiếu tessellation...). Bản DXVK nào cũng cần các tính năng đó. Zink không chạy (dòng bên dưới) thì app nói đúng lý do đó trước | Dùng lại DXVK khi game đang chạy WineD3D, đổi driver. Hộp hiện khi game chạy WineD3D thì từ đó app không đề nghị WineD3D cho game này với driver này nữa (`AgvnOpenGlCheck`, nhớ theo driver và bản app, từ bản 0.1.35) |
| Game Unreal tự dừng vì chờ vẽ hình quá lâu | Hộp "LowLevelFatalError … GameThread timed out waiting for RenderThread after 120.00 secs": phần vẽ hình của Unreal 4 bận liền 120 giây (tải, dựng shader và texture lần đầu trên máy chậm), và Unreal tự dừng (`g.TimeoutForBlockOnRenderFence`). Game không hỏng | Cho game chờ lâu hơn: thêm `-nothreadtimeout` vào tham số chạy của game (bản 0.1.34) |
| Godot không mở được OpenGL | Câu lỗi của Godot (nhật ký `godot.log` hoặc hộp "Unable to initialize video driver"), hoặc game Godot tắt trước khi hiện hình | Chạy Godot bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), bằng Direct3D 11 (ANGLE, Godot 4.4 trở lên), bỏ cách vẽ đã đổi, đổi driver |
| Godot không mở được OpenGL lẫn Direct3D 11 | Hộp lỗi của Godot 4.4 trở lên: "...required OpenGL 3.3 or Direct3D 11 version" | Chạy bằng Vulkan, bỏ cách vẽ đã đổi, đổi driver |
| Godot không mở được Vulkan | Hộp lỗi của Godot: "...required Vulkan version" | Bỏ cách vẽ đã đổi, chạy bằng Direct3D 11 (ANGLE), đổi driver |
| Game Godot tự tắt sau khi đổi cách vẽ | App đã đổi cách vẽ của game, rồi game tự tắt trong một phút đầu mà không để lại câu lỗi nào | Chạy bằng Direct3D 11 (ANGLE), bỏ cách vẽ đã đổi, đổi driver |
| Game bị tắt khi vẽ hình (OpenGL qua Zink) | Zink báo `vkCreateGraphicsPipelines failed` rồi game tự tắt; hay gặp ở GPU Mali, vì Zink thiếu vài tính năng trên đó | Game Godot: chạy bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), bằng Direct3D 11 (ANGLE), bỏ cách vẽ đã đổi. Mọi game: đổi driver |
| Driver đồ họa thiếu tính năng DirectX 11 | DXVK: `D3D11CoreCreateDevice: Requested feature level not supported`: driver không đủ tính năng cho mức DirectX nào game xin (vẽ khung dây, geometry shader, transform feedback...) | Đổi driver, dùng WineD3D (trừ khi OpenGL đã không chạy với driver này), gửi nhật ký. Không đổi bản DXVK: bản nào cũng cần các tính năng đó |
| Wine lỗi khi game gọi dịch vụ của Windows (bản 0.1.52) | Wine: một lỗi truy cập bộ nhớ (`EXCEPTION_ACCESS_VIOLATION`) có khung `L"rpcrt4.dll"` trong backtrace, thường ngay sau đó game đứng màn hình đen (Unreal tự bắt lỗi, Wine không in lỗi nào của nó); tìm cả khi game không tự tắt | Chạy bằng Wine cũ (Proton 9), giả lập CPU ổn định, gửi nhật ký |
| Game khoá chết, đứng hình (bản 0.1.63) | Wine: `err:sync:RtlpWaitForCriticalSection section … wait timed out in thread …, blocked by …` lần thứ hai cho cùng một luồng và một khoá, tức luồng đó chờ khoá hơn 1 phút. App bật sẵn kênh `err+sync` khi nhật ký Wine tắt, và ghi `[AGVN] Khoá chết: …` vào dòng cuối của Wine và `su-kien.txt` (`AgvnLockWatch`); tìm cả khi người chơi tự thoát hay vuốt tắt app. Xem "Game khoá chết khi nạp cảnh" bên dưới | Giả lập CPU giữ đúng thứ tự bộ nhớ, tắt Esync, giả lập CPU ổn định hơn, chạy bằng Wine cũ (Proton 9), chạy không có mod, gửi nhật ký |
| Bản 32-bit của game không dùng được bộ nhớ đồ họa (bản 0.1.51) | DXVK: `DxvkMemoryAllocator: Mapping memory failed with VK_ERROR_MEMORY_MAP_FAILED`: chương trình 32-bit không nhận được bộ nhớ đồ họa với driver này (Mali), nên không có DirectX; tìm cả khi game không tự tắt | Chạy bản 64-bit của game (nếu có), đổi driver, dùng WineD3D, gửi nhật ký. Không đổi bản DXVK: bản nào cũng cần bộ nhớ đó |
| Máy không chạy được OpenGL với driver này | Mesa: `failed to load driver: zink`: Zink không khởi động được trên driver Vulkan của game, nên game OpenGL và WineD3D không vẽ được | Dùng lại DXVK (game đang dùng WineD3D), đổi driver, gửi nhật ký. Từ đó app không đề nghị WineD3D với driver này nữa (`AgvnOpenGlCheck`, nhớ theo driver và bản app), dù game kết thúc thế nào (từ 09/10/2026, cả khi người chơi tự thoát) |
| Không tạo được DirectX / thiếu DirectX 11 | Câu lỗi của DXVK, Unity (`InitializeEngineGraphics failed`) | Đổi bản DXVK, đổi driver (Turnip ↔ System), dùng WineD3D. "Đổi bản DXVK" chỉ hiện khi bản kia chạy được: DXVK 2.x cần Vulkan 1.3, nên với driver của máy (System) thấp hơn, lúc mở game vẫn chạy 1.10.3 và nút đó không hiện (`AgvnDxvkPick.sameAtStart`) |
| DXVK không đọc được texture nén (bản 0.1.45) | DXVK: `D3D11: Cannot create shader resource view`, rồi game tự tắt (game Unreal hiện hộp "LowLevelFatalError … CreateShaderResourceView … failed with error E_INVALIDARG"). Ca đầu, 15/10/2026: Av Director Life, Mali-G925, DXVK 2.3.1. Xem "Texture nén BCn với DXVK 2.x" bên dưới | Đổi DXVK về 1.10.3 (fix `dxvk-old`, có cả khi "Đổi DXVK" đã thử), đổi driver (Turnip tự đọc được BCn), gửi nhật ký |
| Game Unreal báo lỗi rồi tự tắt (bản 0.1.46) | Hộp "The UE-… Game has crashed and will close" của Unreal. Hộp này không phải hộp thông báo của Windows nên Wine không ghi; app đọc tiêu đề của nó từ cửa sổ X (`AgvnUnrealBox`, `WM_NAME`), ghi vào `su-kien.txt` và vào dòng cuối của Wine (`[AGVN] Unreal crash box: …`). Câu lỗi trong hộp thì app chưa đọc được. Dòng của DXVK (`dxvk-view`…) nói rõ hơn thì app báo lỗi đó trước | Đổi DXVK, đổi driver, giả lập CPU ổn định hơn, chạy bằng Wine cũ (Proton 9), về cấu hình gốc, gửi nhật ký |
| Driver đồ họa lỗi giữa chừng | `VK_ERROR_DEVICE_LOST` | Để Turnip tự chọn chế độ dựng hình, đổi driver, đổi DXVK, hạ Đồ họa |
| Hết bộ nhớ đồ họa / hết RAM | DXVK, Unity, Unreal báo hết bộ nhớ; DXVK không gắn được bộ nhớ cho texture (`Failed to bind device memory`, xem ví dụ Boxman bên dưới) | Hạ Đồ họa. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ (`giam-ram.md`) |
| GPU hết bộ nhớ dựng hình (bản 0.1.30) | Driver Mali báo `GROUP_ERROR_TILER_HEAP_OOM` từ 20 lần trong một phiên, khi game đang chạy (xem ví dụ Thanh Âm Mùa Hạ bên dưới) | Hỏi ngay trên thanh trong game: hạ Đồ họa. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ. Phiên đó không được tính là chạy tốt |
| File game hỏng | Unity: `is corrupted` | Gửi nhật ký (cần chép lại game) |
| Không tìm thấy file của game | Hộp "File not found." của Wine: file chạy của game, hoặc file game cần, không còn ở chỗ cũ (thư mục game bị đổi tên, chuyển hay xoá) | Gửi nhật ký (cần chép lại thư mục game, thêm lại game) |
| Game không mở được thư viện DLL của nó (bản 0.1.42) | Hộp "No access to memory location." của Wine (dòng `MSGBOX_OnInit`, có cả khi tắt nhật ký Wine). Ca đầu, 14/10/2026: game RPG Maker VX Ace ở `Download`, Proton 10 + FEX. `DllMain` của `RGSS301.dll` gặp lỗi truy cập bộ nhớ, ngay sau dòng `noexec filesystem, mapped … without PROT_EXEC`. Xem "DLL ở bộ nhớ chung" bên dưới | Chạy nhẹ (game RPG Maker XP/VX/VX Ace), chạy bằng Wine cũ (Proton 9), gửi nhật ký |
| File chạy của game không đúng định dạng | Hộp "Bad EXE format" của Wine: file được mở không phải chương trình Windows mà Wine chạy được (file hỏng hoặc chép chưa xong, chương trình 16-bit hay DOS, không phải file chạy của game). Trước đây app chỉ báo "Game tắt ngay sau khi mở" | Gửi nhật ký (cần chép lại game, hoặc thêm lại game bằng đúng file chạy) |
| Game Unity crash rồi đứng hình | Unity tự bắt crash nên Wine không thấy gì: nhật ký Unity (`Player.log`) có `Crash!!!` và "A crash has been intercepted by the crash handler". App đọc nhật ký này cả lúc game đang chạy (xem ví dụ Become A Vtuber bên dưới) | Giả lập CPU ổn định hơn, Wine cũ (Proton 9), đổi DXVK, đổi driver, về cấu hình gốc, gửi nhật ký |
| Game crash | Wine in báo cáo crash | Chạy nhẹ (game RPG Maker XP/VX/VX Ace đang chạy Windows), đổi DXVK, giả lập CPU ổn định hơn, đổi driver, về cấu hình gốc |
| Game tự tắt lúc máy gần hết RAM | Game tự đóng, không crash, không câu lỗi, trong khi 30 giây trước đó RAM trống đã xuống dưới mức của thanh cảnh báo RAM (một phần mười RAM của máy, từ 600 MB đến 1,2 GB), kể cả khi game chưa kịp hiện hình | Dùng lại cấu hình đã chạy được, hạ Đồ họa, gửi nhật ký. Từ lần mở sau, game tự tiết kiệm RAM như Siêu nhẹ. Game đã tiết kiệm như vậy mà vẫn hết RAM: không đề nghị hạ Đồ họa nữa |
| Game không chịu độ phân giải nhỏ ("The current resolution is too low") | Game tắt trước khi hiện hình, màn hình dưới 480 dòng | Nâng Đồ họa (thường là Thấp 854×480), dùng lại cấu hình đã chạy được |
| Game không chạy với cấu hình mới | Lần trước chạy được, đổi cấu hình xong thì tắt trước khi hiện hình | Dùng lại cấu hình đã chạy được, về cấu hình gốc |
| Game tắt ngay, không rõ lỗi | Game tắt trước khi hiện hình | Chạy nhẹ (game RPG Maker XP/VX/VX Ace đang chạy Windows), về cấu hình gốc, đổi DXVK, đổi driver, gửi nhật ký |

Ví dụ ngày 06/10 (bản 0.1.20): Thorn Sin (Unity 2020.3, DirectX 11) trên Xiaomi 24094RAD4G, GPU PowerVR BXM-8-256,
driver Vulkan 1.1. Cả năm lần chơi, game chạy bằng WineD3D và tắt ở "Failed to initialize graphics": WineD3D vẽ bằng
OpenGL, mà Zink không khởi động được trên driver đó (`failed to load driver: zink`). DXVK cũng không giúp được: theo mã
nguồn DXVK 1.10.3 (`D3D11Device::GetDeviceFeatures`), DirectX 11 cần geometry shader ở mọi mức, vẽ khung dây
(`fillModeNonSolid`) từ mức 9.1 và transform feedback từ mức 10.0; driver PowerVR này thiếu ít nhất hai thứ sau. "Tự
sửa lỗi" chỉ biết "Game báo thiếu DirectX", và vì game đang dùng WineD3D nên hộp hỏi chỉ còn "Gửi nhật ký". Giờ app nói
đúng lý do, đề nghị đưa game về DXVK, và không đề nghị WineD3D với driver đó nữa. Máy chỉ có driver như vậy thì không
chạy được game DirectX 10/11. Game "Chạy nhẹ" (Ren'Py, RPG Maker, Godot) không cần DirectX nên vẫn chạy.

Ví dụ ngày 09/10 (bản 0.1.32): Support Pregnancy School (Unreal) trên Samsung Galaxy M34 (Exynos 1280, GPU Mali-G68,
driver Vulkan 1.1, 7,2 GB RAM), Proton 10. Ở Đồ họa Thấp (854×480), game chạy bằng DXVK 1.10.3 nhưng màn hình đen: GPU
bận 70–98%, không ra khung hình nào trong 35–55 giây cuối, app và game dùng 3 GB RAM. Người chơi báo "Màn hình đen dù
game vẫn chạy", và "Tự sửa lỗi" thử từng cách:
- "Đổi DXVK sang bản 2.3.1": không đổi được gì, vì DXVK 2.x cần Vulkan 1.3 nên lúc mở game app vẫn chạy 1.10.3. Hai lần
  chơi phí công. Giờ nút này không hiện khi bản kia không chạy được với driver của game.
- "Dùng WineD3D": Zink không chạy trên driver này (`failed to load driver: zink`), nên WineD3D không có OpenGL, và
  Unreal hiện hộp "A D3D11-compatible GPU (Feature Level 11.0, Shader Model 5.0) is required to run the engine."
  (ảnh anh Sơn gửi). Phiên đó kết thúc bằng lệnh thoát của app (mã 137) mà không có câu hỏi nào, nên bản 0.1.32 không
  ghi nhận Zink lỗi. Giờ app ghi nhận ngay khi thấy dòng đó, dù game kết thúc thế nào, và không đề nghị WineD3D với
  driver này nữa.
- "Về cấu hình gốc": game dừng ngay lúc mở, `"d3d11.dll" failed to initialize` (thư mục game có sẵn `d3d11.dll` riêng,
  gói GameHub). Nhật ký không cho biết vì sao: logcat đã hết chỗ từ 13:07.

Màn hình đen ở DXVK thì chưa rõ lý do: game có thể vẫn đang tải và dựng shader (Unreal trên GPU Mali yếu, texture BCn
phải giải nén), hoặc bị kẹt. Thử để game chạy 3–5 phút một lần ở Đồ họa Thấp.

Nhật ký thứ hai của máy này (09/10, 14:23–14:35, vẫn bản 0.1.32) có năm lần chơi:
- Lần "Dùng WineD3D" thật ra chạy DXVK: `moi-truong.txt` có `DXVK_CONFIG`, không có `WINE_D3D_CONFIG`, và giống hệt
  cấu hình app trả về sau đó. Cách sửa đã mất trước khi game mở. App vẫn hỏi về WineD3D, người chơi bấm "Vẫn còn lỗi",
  nên app bỏ một cách chưa chạy. Lý do và cách sửa: "Người chơi báo lỗi" bên dưới (bản 0.1.34).
- "Về cấu hình gốc": hộp "Assertion failed! … vkCreateShaderModule" (driver Mali không dựng được shader của DXVK), rồi
  màn hình đen; bật "Đồng bộ khung hình" cũng không hơn, và app báo đã thử hết cách.
- Lần cuối, với cấu hình trước khi thử: đứng hình 25 giây, không ra khung hình nào trong 40 giây cuối, RAM còn 600 MB.

Nhiều khả năng máy này không chạy được game: WineD3D cần Zink mà driver thiếu tính năng Zink cần, còn DXVK thì lỗi
shader hoặc đen màn hình.

Nhật ký thứ ba (09/10, 17:02–17:59, vẫn bản 0.1.32): lần 17:14 chạy 29 phút và tới được menu, nhưng nền phía sau vẫn
đen; lúc cuối GPU bận 64–94% mà không ra khung hình nào trong 60 giây. Bật lại bước sửa shader "cắt hình", giải nén BCn
bằng CPU, đổi tên `d3d11.dll` của gói GameHub: mỗi lần chỉ chạy 1,5–4 phút, vẫn đen. Lần cuối (không còn `d3d11.dll`
của gói, BCn bằng CPU) Unreal hiện hộp "GameThread timed out waiting for RenderThread after 120.00 secs" rồi tự dừng.
Bản 0.1.34 nhận ra hộp đó và đề nghị thêm `-nothreadtimeout` vào tham số chạy để Unreal chờ thay vì tự dừng. Người
chơi có đổi "Mức tính năng VKD3D" (9_1, 11_0), nhưng mức đó chỉ dùng cho game DirectX 12, không đổi gì với game
DirectX 11 này.

Ví dụ ngày 10/10 (bản 0.1.27): MK Days (Unreal, gói Việt hoá có cheat AGVN) trên Xiaomi 23090RA98G (Dimensity 7200,
GPU Mali-G610, driver của máy, Vulkan 1.3). Hai lần đầu chạy Proton 10 với DXVK, lần thứ ba chạy Proton 9: cả ba lần
đều hiện hộp "Assertion failed! … vkCreateShaderModule" (dòng 3520 của Proton 10, dòng 3275 của Proton 9), nên đổi
Wine không giúp được. Cả ba lần hai bước sửa shader cho Mali vẫn bật, và người chơi vuốt tắt AGVN thay vì bấm OK, nên
bản 0.1.27 không hỏi gì (từ bản 0.1.33 app vẫn hỏi sau khi vuốt tắt). Lần thứ tư người chơi tự chọn WineD3D: Zink chạy
nhưng thiếu ba tính năng trên Mali-G610 (`logicOp`, `fillModeNonSolid`, `shaderClipDistance`), và Unreal hiện hộp
DirectX 11 mức 11.0 bằng tiếng Nhật. App chưa đọc được câu tiếng Nhật nên báo nhầm "Game tắt ngay sau khi mở". Từ bản
0.1.35 app nhận ra câu đó bằng mọi thứ tiếng (trong hộp lỗi có "D3D11", mức 10.x hay 11.x rồi shader model 4.x hay
5.x), và không đề nghị WineD3D cho game này với driver này nữa. Với game này nên thử: dùng lại DXVK, tắt bước sửa
shader "hằng số", rồi bước "cắt hình".

Ví dụ ngày 06/10 (bản 0.1.23): Become A Vtuber (Unity 6, DirectX 11) trên Redmi K30 5G, GPU Adreno 620, chạy bằng
Proton 10. Cả năm lần chơi, game crash ở cùng một chỗ (`UnityPlayer.dll+0x8d8b1a`) lúc tải cảnh đầu; dòng cuối trước
crash là cảnh báo về ảnh `mini game1_great`. Người chơi đã đổi bản Turnip và bật DXVK async mà chỗ crash không đổi.
Unity tự bắt crash này (`Player.log` có `Crash!!!`, danh sách module, ngăn xếp và "A crash has been intercepted by the
crash handler"), nên Wine không báo gì và game đứng hình mãi. Người chơi rời app rồi vuốt tắt AGVN, nên "Tự sửa lỗi"
không hỏi gì. Giờ app đọc nhật ký Unity mỗi 3 giây trong lúc game chạy (`AgvnUnityCrashWatch`). Thấy crash thì app ghi
vào `su-kien.txt`, hiện thanh "Game bị lỗi và đã dừng" và đóng game sau 10 giây. 10 giây này chỉ tính lúc người chơi ở
trong app. Về thư viện, app hỏi `unity-crash`, nút đầu là giả lập CPU ổn định hơn: chỗ crash nằm trong mã của Unity,
phần app chạy qua giả lập CPU (FEX), còn đổi driver hay DXVK async thì chỗ crash vẫn vậy. Phiên đã bị vuốt tắt sau
crash cũng được hỏi ở lần mở app sau, và `tom-tat.txt` ghi "Game bị lỗi (crash) – Unity báo crash trong …; sau đó: …".
Nếu Unity kịp ghi báo cáo (`error.log` trong thư mục `Crashes` mà nhật ký nhắc), app chép nó vào phiên chơi thành
`unity-crash.txt`, và tóm tắt ghi thêm tên lỗi, như "Access Violation 0xc0000005, đọc 0x0". Unity cũng bắt cả crash lúc
game đang thoát, nên hộp hỏi có câu "Nếu chính bạn vừa thoát game thì bỏ qua thông báo này", như thông báo hết RAM.

Ví dụ ngày 06/10 (bản 0.1.23): Boxman (Unity 2019.4, DirectX 11) trên Galaxy SM-S947B, GPU Samsung Xclipse 960, driver
của máy, Proton 10. Cả năm lần chơi, DXVK không gắn được bộ nhớ cho một texture (`DxvkImage::DxvkImage: Failed to bind
device memory`); Unity không tạo được texture đó (`d3d11: failed to create 2D texture … [D3D error was 80070057]`, lần
đầu là ảnh 4096×8192), rồi Unity tự bắt crash và game đứng hình (bốn lần), hoặc đứng 20 giây rồi người chơi thoát. Lúc
đó DMA-BUF (bộ nhớ đồ họa) của game lên tới khoảng 1 GB. Đổi giả lập CPU hay Wine không giúp được lỗi này, nên từ bản
0.1.28 dòng đó thuộc lỗi `gpu-memory` (xét trước `unity-crash`): app đề nghị hạ Đồ họa, và từ lần mở sau dùng mức tiết
kiệm RAM cao nhất của game (Unity ở chất lượng thấp nhất: texture nhỏ hơn, ít bộ nhớ đồ họa hơn).

Ví dụ ngày 07/10 (bản 0.1.28): Thanh Âm Mùa Hạ (Unreal, `GalHome-Win64-Shipping.exe`, có mod UE4SS) trên Xiaomi
25060RK16C, GPU Mali-G925-Immortalis, driver của máy. Bốn trong năm lần chơi, driver Mali báo `Received a
GROUP_ERROR_TILER_HEAP_OOM error` khoảng 0,6 giây một lần (30–80 lần trong `wine-cuoi.txt`). Đó là lúc GPU hết bộ nhớ
để chia hình thành từng ô màn hình (tiler heap), nên phải vẽ mỗi khung hình thành nhiều phần. Lúc đó:
- game khựng 1–5 giây liên tục, có lúc đen màn hình;
- game và app dùng tới 5,5–5,9 GB RAM, máy chỉ còn 0,6–0,7 GB trống;
- người chơi tự thoát sau hơn một phút.

Lần chơi duy nhất không có lỗi này chỉ dùng 2,7 GB.

Trước bản 0.1.30, người chơi tự thoát sau một phút thì app coi là lần chạy tốt và lưu cấu hình đó. Giờ thì khác:
- `AgvnWineTail` đếm mọi dòng `TILER_HEAP_OOM`, kể cả những dòng bị gộp trong `wine-cuoi.txt`;
- từ 20 lần, thanh trong game hỏi ngay ("GPU hết bộ nhớ dựng hình", lỗi `gpu-tiler`, `when: live`) và đề nghị hạ
  Đồ họa. Từ lần mở sau, game dùng mức tiết kiệm RAM cao nhất (`AgvnMemorySaver.markRamShort`; với Unreal là texture
  pool nhỏ hơn);
- phiên đó không được tính là chạy tốt (`AgvnEvidence.good`);
- `su-kien.txt` ghi số lần.

Lỗi này chỉ nhận ra qua dòng driver in ra, không dựa vào loại GPU. Máy nào báo thì được hỏi, máy chạy tốt không bị
hỏi.

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
| Game bị tắt đột ngột | Android ghi tiến trình của Ren'Py hoặc mkxp-z bị lỗi (`CRASH`, `CRASH_NATIVE`). Từ bản 0.1.42, phiên đó được lưu vào `AGVN-Player/logs/<tên game>/<giờ>/`: `tom-tat.txt`, và với lỗi mã máy, `loi-ma-may.txt` (tín hiệu, địa chỉ lỗi, các dòng `#00 pc …` đọc từ tombstone của Android 12 trở lên, `AgvnTombstone`) | Chạy bằng Windows, gửi nhật ký |
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
như máy tắt), nên trước khi tắt nó ghi lại là người chơi vuốt tắt, cùng các dòng cuối của Wine (`wine-cuoi.txt`).
Lần mở sau, `tom-tat.txt` ghi "Người chơi vuốt tắt AGVN khỏi danh sách app gần đây", và app chỉ hỏi khi các dòng đó tự
nói lên một lỗi (hộp "Assertion failed!" của driver Vulkan, hộp DirectX 11 của game Unreal). Ngày 09/10/2026, Support
Pregnancy School (Unreal, Mali-G615, Proton 10) hiện hộp `vkCreateShaderModule`, người chơi vuốt tắt AGVN, và trước
bản sửa không có cách sửa nào được đưa ra (`AgvnDoctor.afterKill`). Muốn thoát game thì bấm "✕ Thoát" trên
thanh ⌨ ✎ 👁 ⛶ của game PC (app hỏi lại "Thoát game?"), hoặc "Thoát" trong menu bên trái của game "Chạy nhẹ".

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

## Người chơi báo lỗi (thử lần lượt, giữ cách hết lỗi)

App tự thấy các lỗi để lại dấu vết: crash, game tắt ngay, màn hình đen lúc mở, game chậm, hết RAM, phim không
chạy, Unity tự bắt crash. Còn hình chập chờn, đốm đen, chữ lỗi hay mất tiếng thì không để lại gì trong nhật ký, và
app không nhìn ra chắc chắn từ hình. Với những lỗi đó, người chơi nói app biết game đang bị gì, rồi app tự chữa:

1. Chọn "Tự sửa lỗi" trong menu ⋮ của game ở thư viện, hoặc bấm "🩺 Tự sửa" trên thanh trong game. App hỏi "Game
   đang bị gì?" (các lỗi `report-*` trong `game-problems.json`).
2. App nói lỗi đó thường do đâu và liệt kê các cách nó sẽ thử, theo thứ tự (chỉ những cách đổi được gì đó cho game
   này). Bấm "Thử cách 1": app đổi cấu hình của riêng game đó rồi mở lại game.
3. Sau khi game hiện hình và chạy được 45 giây (thời gian người chơi rời app không tính), thanh trên game hỏi "Còn lỗi
   … không?" (`AgvnRepairAsk`). Game "Chạy nhẹ" thì người chơi trả lời ở thư viện: bấm lại "Tự sửa lỗi".
   - **Hết lỗi rồi:** app giữ cách đó, và cấu hình này thành cấu hình đã chạy được của game.
   - **Vẫn còn lỗi:** app trả mọi cấu hình về như trước khi thử cách đầu tiên, rồi đặt cách tiếp theo và hỏi "Mở lại
     game ngay". Hết cách: cấu hình về như cũ, app mời "Gửi nhật ký".
   - **Hỏi lần sau:** lần mở game sau app hỏi lại.
   - **Game đang không vẽ gì thì chưa hỏi (bản 0.1.50):** tới lúc hỏi mà 5 giây qua game không vẽ khung nào, có thể game
     đang tải. App chờ game vẽ lại rồi mới hỏi. Game đứng yên 3 phút liền thì app hỏi luôn (`AgvnRepairAsk.waitsForFrames`;
     thời gian app dừng game không tính). `su-kien.txt` ghi một dòng "Tự sửa lỗi: game không vẽ khung nào từ … giây (có
     thể đang tải)…". Nhật ký Immoral Bathhouse (Unity, Adreno 725, 17/10/2026): game vẽ logo, rồi không vẽ gì 2–2,5 phút
     trong lúc tải cảnh đầu. App hỏi ở giây 45, người chơi bấm "Vẫn còn lỗi" cho 4 cách liền, và mỗi lần mở lại cắt ngang
     lúc game đang tải.

App chỉ hỏi về một cách khi game đã chạy lại với cách đó (bản 0.1.27; `AgvnRepair.ran`: giờ đặt cách, `report.at`, so
với lần mở game gần nhất, `lastRunAt` của lối tắt). Trước đó, bấm lại "Tự sửa lỗi" hay "🩺 Tự sửa" thì hộp nói "game
chưa chạy lại với cách này" và chỉ có "Mở lại game ngay" (trong game) hoặc "Chơi thử với cách này" (ở thư viện), cùng
"Chọn lỗi khác". Nhật ký Isekai NTR Inn (07/10/2026) cho thấy vì sao: người chơi bấm "Vẫn còn lỗi" cho "Về cấu hình gốc"
11 giây sau khi app đặt cách đó, lúc game vẫn chạy với cấu hình cũ, nên app bỏ một cách chưa ai thử và báo "đã thử hết".

Game cũng phải chạy với đúng cách đang thử (bản 0.1.34). Nhật ký Support Pregnancy School trên Galaxy M34 (09/10/2026,
bản 0.1.32) cho thấy cách sửa có thể mất trước khi game mở: app đang thử "Dùng WineD3D" mà game chạy DXVK, đúng cấu
hình trước lần thử. Thư viện và trang game (bấm ảnh game) giữ cài đặt game đọc lúc chúng hiện, và mỗi lần lưu thì ghi
lại cả bản đó: cách sửa đặt sau đó (trong game, của "Tự sửa lỗi" hay hộp hỏi sau khi chơi, hay dấu hết RAM của game bị
vuốt tắt) bị ghi đè khi bấm tim, chỉnh "Cấu hình", hay bấm "Chơi" ở thư viện. Hai việc đã sửa:
- Thư viện và trang game đọc lại file cài đặt của game trước khi làm gì với game (`AgvnShortcutReload`).
- Lúc game mở, app xem cách đang thử có trong cấu hình game không (`AgvnRepairCheck`; các cách đổi một công tắc:
  WineD3D, về DXVK, DXVK khác, driver khác). Không có (người chơi tự đổi trong "Cấu hình", hay bị ghi đè) thì app không
  hỏi về cách đó và không tính là đã thử. `su-kien.txt` có "Tự sửa lỗi: game chạy không có …", và thanh "Chưa thử được:
  …" mời thử cách còn lại đầu tiên (thường chính là cách đó). Ở thư viện, bấm "Tự sửa lỗi" thì app báo như vậy rồi
  hiện lại danh sách cách còn lại.

Cách đang thử mà game tự tắt (hay crash) trước một phút thì app không chờ hỏi nữa (bản 0.1.45, `AgvnRepairEnd`). Thanh
chỉ hỏi sau 45 giây chơi, nên trước đây một cách làm game tắt sớm hơn thì không bao giờ được hỏi và cứ ở lại trên game:
Av Director Life (15/10/2026) giữ "Đổi DXVK sang bản 2.3.1" và tắt ở mọi lần mở (xem "Texture nén BCn với DXVK 2.x").
Giờ, khi game có bật cách đó và tự tắt trong 60 giây đầu, app làm như khi người chơi bấm "Vẫn còn lỗi": tính là cách đó
không giúp, trả cấu hình về như trước khi sửa. Về thư viện, app mở lại lỗi người chơi đã báo, nói cách nào vừa làm game
tự tắt sau bao nhiêu giây, và có nút thử cách tiếp theo. Người chơi tự thoát game thì app vẫn hỏi như trước. Thanh màn
hình đen chuyển game sang Proton 9 ("Chạy bằng Wine cũ") lúc đang thử một cách cũng trả cách đó về trước khi chuyển.
Phiên mà app có lúc dừng game (người chơi rời app, hay bấm ⏸) thì không tính theo cách này (bản 0.1.46,
`AgvnSessionTrack.stopped`): ở bản 0.1.45, Rj01608199 chết mỗi lần người chơi quay lại app (xem "Game chết khi quay lại
app"), và hai cách bị tính là không giúp sau "42 giây", trong đó 28 giây game đang bị dừng.

Chỉ game được báo lỗi bị đổi, và chỉ sau khi người chơi báo. Cách không giúp được không ở lại trên game, nên game không
phải trả giá (chậm hơn, nóng hơn) cho một cách sửa vô ích. App ghi mọi bước vào `su-kien.txt` của phiên chơi ("Tự sửa
lỗi: người chơi báo …, thử: …"). Trạng thái nằm cùng file của "Tự sửa lỗi" (`files/agvn/doctor/`, các khóa
`report.*`): lỗi đang sửa, cách đang thử, các cách đã thử và cấu hình trước khi thử (`AgvnRepair`).

| Người chơi chọn | App thử, theo thứ tự |
|---|---|
| Hình chập chờn, nhấp nháy | Tắt DXVK async; bật Đồng bộ khung hình; Godot: Chạy nhẹ, cách vẽ khác; Turnip vẽ cả khung hình trong bộ nhớ (sysmem); tắt LRZ của Turnip; đổi DXVK; đổi driver; về cấu hình gốc |
| Hình có đốm đen, mảng đen, sọc hoặc sai màu | Godot: Chạy nhẹ, cách vẽ khác; tắt LRZ của Turnip; tắt nén hình UBWC; sysmem; giải nén mọi texture BCn (GPU không phải Turnip); đổi DXVK; đổi driver; để game Unity tự chọn chất lượng; WineD3D; về cấu hình gốc |
| Màn hình đen dù game vẫn chạy | Đồng bộ khung hình; Godot: Chạy nhẹ, cách vẽ khác; sysmem; nâng Đồ họa; đổi DXVK; đổi driver; WineD3D; chạy không có mod; về cấu hình gốc |
| Game chậm, giật, lag | Godot: Chạy nhẹ; hạ Đồ họa; DXVK chạy thẳng trên chip ARM; chế độ Gmem; để Turnip tự chọn; giả lập CPU nhanh hơn; bỏ bớt khung hình (RPG Maker); mở cài đặt Tiết kiệm pin |
| Game đứng hình, treo | Giả lập CPU ổn định hơn; chạy không có mod; giả lập CPU giữ đúng thứ tự bộ nhớ; tắt Esync; Godot: Chạy nhẹ; tắt DXVK async; Godot: cách vẽ khác; đổi DXVK; đổi driver; về cấu hình gốc |
| Game tự tắt, văng ra | Giả lập CPU ổn định hơn; để game Unity tự chọn chất lượng; chạy không có mod; Godot: Chạy nhẹ, cách vẽ khác; đổi DXVK; đổi driver; về cấu hình gốc |
| Không có tiếng, tiếng rè hoặc lắp | Đổi giữa PulseAudio và ALSA; về cấu hình gốc |
| Chữ lỗi, ô vuông, ký tự lạ | Chạy game bằng tiếng Nhật; bằng tiếng Trung; về cấu hình gốc |
| Hình mờ, vỡ hoặc quá nhỏ | Nâng Đồ họa; Godot: Chạy nhẹ; để game Unity tự chọn chất lượng |
| Hoạt ảnh, video hoặc hiệu ứng không chạy (bản 0.1.28) | Godot: Chạy nhẹ, cách vẽ khác; tắt DXVK async; nâng Đồ họa; để game Unity tự chọn chất lượng; đổi DXVK; đổi driver; WineD3D; về cấu hình gốc |
| Chạm, bấm không ăn (bản 0.1.31) | Gửi cú chạm như chuột thật, hoặc như trước với game đang bật cách đó; chạy bằng bản Windows (game Chạy nhẹ); về cấu hình gốc |
| Lỗi khác | Dùng lại cấu hình đã chạy được; về cấu hình gốc; giả lập CPU ổn định hơn; Godot: Chạy nhẹ, cách vẽ khác; đổi DXVK; đổi driver; chạy không có mod |

Mọi lỗi đều kết thúc bằng "Gửi nhật ký". Game "Chạy nhẹ" có thêm "Chạy bằng bản Windows" khi thư mục có file `.exe`.
Không có "Wine cũ (Proton 9)" trong danh sách: chuyển game sang Wine khác thì không trả về như cũ được, nên cách này
chỉ có ở hộp hỏi tự động (crash, game tắt ngay).

Các cách sửa mới (`AgvnRepairFixes`), mỗi cách đổi một cấu hình của riêng game:

- **Turnip: sysmem, tắt LRZ, tắt UBWC** (`TU_DEBUG`, cờ cũ của game giữ nguyên). Đây là các nguyên nhân hay gặp của
  hình chập chờn, đốm đen, sai màu trên Adreno. Chỉ có khi game chạy driver Turnip.
- **Tắt DXVK async** (`async=0`): DXVK dựng xong pipeline rồi mới vẽ, nên không có vật thể nhấp nháy hay hiện thiếu.
  Lúc đầu game có thể giật hơn. Chỉ có khi async đang bật.
- **Giải nén mọi texture BCn** (`bcnEmulation=full`): cho driver không đọc được texture nén (không phải Turnip).
- **Để game Unity tự chọn chất lượng** (`agvnUnityOwnQuality=1`): ở Đồ họa Thấp và Siêu nhẹ, app ép Unity mở game ở
  mức chất lượng thấp nhất (`AgvnUnityQuality`); cách này bỏ việc đó cho riêng game, Đồ họa vẫn giữ màn hình và FPS
  của mức đã chọn. Hộp hỏi "Game bị lỗi và đứng hình" (Unity tự bắt crash) cũng đề nghị cách này đầu tiên.
- **Đổi cách phát tiếng:** PulseAudio ↔ ALSA.
- **Tiếng Nhật, tiếng Trung** (`LC_ALL` = `ja_JP.UTF-8`, `zh_CN.UTF-8`).
- **Game Godot** (bản 0.1.28, sau nhật ký Train45 ngày 07/10/2026: game Godot 4.5 chạy qua Wine, khựng 1–10 giây,
  người chơi báo cảnh có hoạt ảnh bị đứng):
  - **Chạy nhẹ** (`godot-light`): game người chơi đã chuyển sang "Chạy bằng Windows" được chạy lại bằng Godot cho
    Android, khi Godot 4.7 đọc được cả gói game (`AgvnGodotLight.fit`). Không qua Wine và giả lập CPU nên mượt hơn
    nhiều. Có cả ở hộp hỏi tự động khi game chậm và khi game Godot không chạy được qua Wine. Từ bản 0.1.28, engine
    của app đọc được cả game Godot 4.3 và 4.4 (bản vá 0003, 0004; xem `engine-rieng.md`).
  - **Cách vẽ khi Chạy nhẹ** (`godot-light-renderer`): đổi sang cách còn lại của Godot cho Android, OpenGL
    (Compatibility) hoặc Vulkan (Mobile). App đọc cách game tự chọn cho điện thoại trong `project.binary`
    (`rendering/renderer/rendering_method.mobile`); game không ghi gì là Vulkan. Cách đã chọn nằm ở
    `agvnGodotRenderer` và được trả về như cũ nếu không giúp được.
  - **Cách vẽ khi chạy qua Wine:** Vulkan (`godot-renderer`, GLES2 với Godot 3) và Direct3D 11 qua ANGLE
    (`godot-angle`, từ Godot 4.4). Lỗi người chơi báo giờ mang theo bản Godot của game (`AgvnGodotGame.params`),
    nên app chọn đúng cách cho Godot 3 hay 4.
- **Chạy không có mod** (`mods-off`, bản 0.1.29): game có bộ nạp mod (thư mục `BepInEx`, `MelonLoader` hay `ue4ss` và
  file DLL của bộ nạp cạnh exe: `winhttp.dll`, `version.dll`...) chạy với bản DLL của chính Wine
  (`WINEDLLOVERRIDES=winhttp=b`, `AgvnMods`). Bộ nạp không chạy nên mọi mod cũng không chạy; file của game giữ nguyên.
  Có ở "Game đứng hình, treo", "Game tự tắt", "Màn hình đen", "Lỗi khác" và ở hộp hỏi tự động khi game crash hay tắt
  ngay. Không giúp được thì cấu hình về như cũ (mod chạy lại). Giúp được thì người chơi biết lỗi do mod: game chạy như
  bản gốc, không có mod. "Về cấu hình gốc" bật lại mod.
- **Gửi cú chạm như chuột thật** (`raw-mouse`, bản 0.1.31): bật hoặc tắt cách gửi cú chạm của mục "Chạm như chuột thật"
  bên dưới cho riêng game (`agvnRawMouse` = `1` hay `0`). Không giúp được thì cấu hình về như cũ.

## Chạm như chuột thật (game Windows)

Từ bản 0.1.31 (`AgvnRawMouse`). Phiếu ngày 08/10/2026: Open At Nine (Unity 2020.3.25f1 Mono) vào màn tiêu đề, hình và
tiếng đúng, nhưng chạm nút menu không có gì xảy ra; chỉ phần đọc chuột kiểu cũ của Unity (`Input.GetMouseButton`) nhận
cú chạm.

- **Vì sao:** game dùng Input System của Unity cho menu (`InputSystemUIInputModule`). Trên Windows, Input System chỉ
  đọc chuột từ raw input (`WM_INPUT`). Wine tạo `WM_INPUT` từ `SendInput`, nhưng không tạo từ sự kiện chuột của X
  server, vì X server của app không có XInput2 (nhật ký Wine: `x11drv_xinput2_init XInput 2.0 not available`). Cú chạm
  trước đây chỉ đi qua X server, nên game kiểu này không nhận được.
- **Cách sửa:** với game đó, cú chạm (dời chuột, nhấn và nhả nút, lăn chuột) đi qua `agvn-winhandler.exe`. Đây là
  `winhandler.exe` của Winlator, dựng lại từ `app/src/main/cpp/winlator/winhandler.c` (script
  `scripts/agvn/winhandler/build-winhandler.sh`), có thêm lệnh `RC_AGVN_POINTER`: một lần `SendInput` dời chuột tới
  đúng điểm ảnh rồi nhấn hoặc nhả nút ở đó. Nhờ vậy vị trí cú bấm luôn đúng, và game nhận cả `WM_LBUTTONDOWN` lẫn
  `WM_INPUT` như với chuột thật. X server vẫn biết con trỏ ở đâu (để vẽ con trỏ), nhưng không gửi sự kiện cho Wine nữa.
  Vì thế Wine không nhận thêm một lần dời chuột thứ hai, đến muộn. Từ bản 0.1.37, chương trình này đọc dòng lệnh bằng
  Unicode (xem "Thư mục game có dấu"). Bản dựng bằng mingw-w64 GCC 13 của Ubuntu 24.04 (gói
  `gcc-mingw-w64-x86-64-win32`) cho đúng một file, SHA-256
  `a513695104c1c4901ee104ebf147098f6eb6fa477c0e6bd92c66445eb301a9d1` (bản 0.1.31–0.1.36:
  `b6a5dd20a0c229973383b720d6c908a34d2051eb8627ba9b05c51afa5be4a4e8`).
- **Game nào:** game Unity có Input System, tức có `Unity.InputSystem.dll` trong `<tên game>_Data/Managed` (Mono), hoặc
  có chữ `Unity.InputSystem` trong `il2cpp_data/Metadata/global-metadata.dat` (IL2CPP) (`AgvnUnityInput`). Game khác
  bật được ở "Tự sửa lỗi" → "Chạm, bấm không ăn". Ở đó, game đang bật cũng tắt được.
- **Không đổi gì cho các game khác:** chỉ game bật cách này mới chạy qua `agvn-winhandler.exe` (app chép file vào
  `C:\windows` của container khi mở game). Các game khác vẫn dùng `winhandler.exe` của Winlator và sự kiện của X server
  như trước. Chế độ chuột tương đối của Winlator vẫn như cũ.
- **Nhật ký:** `su-kien.txt` có dòng `Chạm gửi như chuột thật, có raw input (qua agvn-winhandler.exe): …` và lý do.
- **Chưa đo trên máy:** Wine của app (Proton 9, Proton 10) tạo `WM_INPUT` từ `SendInput` theo cách của Windows. Chế độ
  chuột tương đối của Winlator cũng dựa vào điều này. Cần thử Open At Nine để chốt.

## Thư mục game có dấu (game Windows)

Từ bản 0.1.37 (`AgvnStarter`). Ngày 10/10/2026, game "GAMEHUB" (Lifeguard Holic, Unity) trên Xiaomi 23090RA98G hiện hộp
"File not found." ngay khi mở, trong khi GameHub mở được game.

- **Vì sao:** `winhandler.exe` của Winlator, chương trình mở game, đọc dòng lệnh theo bảng mã Windows (`__getmainargs`,
  `ShellExecuteExA`). Với game không đặt ngôn ngữ riêng, bảng mã là 1252, nên các chữ như "ệ", "ư", "đ" trong đường dẫn
  bị mất và Wine không tìm thấy file. Bản `agvn-winhandler.exe` của bản 0.1.31–0.1.36 cũng vậy, vì dựng từ cùng mã.
- **Cách sửa:** `agvn-winhandler.exe` đọc dòng lệnh bằng Unicode (`CommandLineToArgvW`, `ShellExecuteExW`). Game có chữ
  ngoài ASCII trong đường dẫn file chạy hay trong tham số chạy thì mở bằng chương trình này, giống game Unity có Input
  System. Cú chạm vẫn đi qua X server như trước; chỉ game cần raw input mới gửi cú chạm qua `SendInput`. Game có đường
  dẫn không dấu không đổi gì, vẫn mở bằng `winhandler.exe` của Winlator.
- **Phần Wine trên máy:** Proton 9 và Proton 10 của app dựng cho Android, luôn đặt tên file theo UTF-8 như Android:
  `ntdll.so` của hai bản không có bảng tên bảng mã của Linux (`ANSIX341968`, `EUCJP`...) và không gọi `nl_langinfo`.
  Vì vậy đường dẫn Unicode tới được đúng file, dù `LC_ALL` để trống.
- **Đã thử (Wine 9.0 trên máy Linux, đặt tên file UTF-8 như Android):** một file chạy thử ghi lại dòng lệnh của nó, mở
  qua `explorer /desktop=shell,1280x720 <chương trình> /dir <thư mục> "<file chạy>"` như app:

  | Thư mục | `winhandler.exe` của Winlator | `agvn-winhandler.exe` bản 0.1.36 | `agvn-winhandler.exe` bản 0.1.37 |
  |---|---|---|---|
  | `New Folder 1/GAMEHUB` | mở được | mở được | mở được |
  | `Lifeguard Holic Việt Hóa/GAMEHUB` | "File not found." | "File not found." | mở được |
  | `ゲーム フォルダ/GAMEHUB` | | | mở được |

  Bản 0.1.37 còn được thử với `/affinity`, với file chạy tên có dấu và tham số chạy có dấu (`"tên có dấu"` tới game
  nguyên vẹn).
- **Nhật ký:** `su-kien.txt` có dòng `Đường dẫn game có chữ ngoài ASCII (ệ, ó): mở game bằng agvn-winhandler.exe…`, và
  dòng `File chạy: …` có `chữ ngoài ASCII: …`.

## Thoát game: để game tự đóng và lưu (game Windows)

Từ bản 0.1.39 (`AgvnGracefulExit`). Phiếu ngày 08/10/2026: khách cập nhật AGVN Player rồi mở lại game thì mất save.

- **Cập nhật app không xoá save:** mọi bản từ 0.1.4 cùng một khoá ký, cài đè giữ nguyên dữ liệu; imagefs không cài
  lại (giữ `home`), container và prefix Wine không bị tạo lại.
- **Lỗi thật:** nút "Thoát" trong menu tắt Wine ngay (SIGKILL trình chạy, SIGTERM các tiến trình Wine), game không được
  đóng như trên PC. Unity chỉ ghi PlayerPrefs (nhiều game Unity cất tiến độ ở đó) vào registry khi nó thoát; game khác
  cũng hay lưu lúc đóng. Tiến độ chưa ghi bị mất, và lần mở sau (thường là sau khi cập nhật app) khách thấy save cũ.
- **Cách sửa:** "Thoát" gửi cho cửa sổ game `WM_DELETE_WINDOW` (như nút X của trình quản lý cửa sổ; Wine đổi thành
  `WM_CLOSE`). App chờ tối đa 5 giây, rồi tắt Wine như trước. Hết chờ sớm khi cửa sổ và tiến trình của game đều không còn,
  hoặc khi game mở thêm một cửa sổ (hộp "thoát không?" của KiriKiri...): khi đó app tắt game ngay như trước. Game đang
  tạm dừng (⏸) được chạy lại để trả lời.
- **Cái giá:** game hỏi xác nhận ngay trong màn chơi (Ren'Py chạy bằng Windows) phải chờ đủ 5 giây mới tắt. Các cách
  tắt khác (hết RAM, game treo, chuyển sang Proton 10) vẫn tắt ngay.
- **Nhật ký:** `su-kien.txt` có `Thoát: game tự đóng sau … ms`, `Thoát: game mở hộp hỏi khi đóng, app tắt game` hoặc
  `Thoát: game không tự đóng sau 5 giây, app tắt game`.

## Bàn phím, chuột ngoài (game Windows)

Từ bản 0.1.32 (`AgvnInputDevices`). Phiếu ngày 08/10/2026: Av Director Life (Unreal) trên Lenovo TB321FU, bản 0.1.30.
Người chơi nối bàn phím và chuột không dây nhưng game không nhận. Khi mở màn gán phím cho thiết bị ngoài, app văng
(`NullPointerException` ở `ControlsProfile.save`).

- **Vì sao không nhận phím:** Android coi nhiều bàn phím không dây và bộ thu USB (bàn phím kèm chuột) là cả tay cầm, vì
  bảng mô tả HID của chúng có nút tay cầm (nguồn có `SOURCE_GAMEPAD`). Winlator thấy thiết bị là tay cầm thì bỏ mọi phím
  của nó khỏi bàn phím game (`Keyboard.onKeyEvent`), còn phím mũi tên thành D-pad. Game không nhận được phím nào.
- **Cách sửa:** một phím chỉ dành cho tay cầm khi tay cầm bấm được nó: nút tay cầm (`KeyEvent.isGamepadButton`), hoặc
  mọi phím của thiết bị không có phím chữ (tay cầm thật). Phím chữ, mũi tên, Enter, Esc của bàn phím đầy đủ luôn tới
  game. Phím Android (quay lại, âm lượng) của bàn phím đó cũng hoạt động lại. Tay cầm thật không đổi gì.
- **Màn gán phím:** thiết bị ngắt kết nối (ngủ, rút ra) trước khi màn mở hoặc mở lại (xoay máy) thì app báo "Thiết bị
  này vừa ngắt kết nối…" và đóng màn, không văng.
- **Nhật ký:** `thiet-bi.txt` có dòng `Bàn phím, chuột, tay cầm ngoài: …` với tên, loại (bàn phím chữ, chuột, tay cầm)
  và nguồn Android báo. `su-kien.txt` có các thiết bị đang nối lúc mở game. Mỗi thiết bị, mỗi lần chơi có một dòng:
  `Bàn phím "…": phím tới game` hoặc `phím … không tới game (…)`, `Chuột "…": app nhận cú bấm (…)`. Nếu "Tắt chuột"
  đang bật, dòng chuột ghi rõ game không nhận. Không có dòng nào nghĩa là Android không gửi phím, cú bấm cho app.
- **Chuột ngoài không làm gì (09/10/2026, bản 0.1.32):** app giữ chuột (`cursor_lock`, bật sẵn), và Android gửi chuột
  bị giữ tới ô đang được chọn. Bộ phím ảo hiện lên thì được chọn (để nhận cần analog của tay cầm), mà AGVN hiện bộ phím
  cho mọi game, nên mọi cú di, cú bấm của chuột USB hay Bluetooth tới bộ phím ảo, và nó bỏ đi. Giờ bộ phím ảo chuyển
  chuột bị giữ sang cùng chỗ với vùng chạm (`handleCapturedPointer`). Đóng menu bên trái thì game lấy lại ô được chọn,
  vì menu giữ nó sau khi đóng (`XServerDisplayActivity.agvnFocusGame`).
- **Chuột ngoài bấm được mà không thấy con trỏ (10/10/2026, bản 0.1.35):** AGVN thêm game Windows với "Giả lập màn
  hình cảm ứng" bật (hồ sơ game): chạm ở đâu thì bấm ở đó, và màn hình game không bao giờ vẽ con trỏ. Giờ con trỏ hiện
  khi chuột thật hay bàn di chuột (touchpad) di, bấm hay lăn, và ẩn lại khi ngón tay chạm màn hình, nên chơi bằng tay
  vẫn như cũ (`AgvnMouseCursor`). Game không giả lập cảm ứng thì vẫn hiện con trỏ như trước.

## Chữ vẽ bằng DirectWrite không hiện (game Windows)

Từ bản 0.1.40 (`AgvnDwriteFonts`). Báo cáo ngày 12/10/2026 (bản 0.1.32, Proton 10, POCO F8 Pro): game vẽ chữ bằng
DirectWrite + Direct2D (`dwrite.dll`, `d2d1.dll`) chạy đủ hình, tiếng nhưng menu và hộp thoại không có chữ. Nhật ký Wine
có `create_local_cached_stream Failed to open … C:\windows\fonts\tahoma.ttf` rồi, mỗi khung hình,
`layout_run_get_last_resort_font Failed to create last resort font`. Lỗi chung của app, không riêng game nào.

- **Vì sao:** DirectWrite của Wine chỉ biết các phông ghi trong khoá
  `HKLM\Software\Microsoft\Windows NT\CurrentVersion\Fonts`; mục ghi tên file trần là file trong `C:\windows\Fonts`.
  Mẫu prefix của Proton 9 và Proton 10 ghi Tahoma, Marlett, Symbol, Wingdings, SimSun, Malgun Gothic, Nirmala UI,
  MS Gothic, Microsoft YaHei… nhưng thư mục đó không có các file này. Phông app thêm vào thư mục (Verdana, Georgia,
  Trebuchet MS, Source Han Sans CN…) thì không có mục nào, nên DirectWrite chỉ thấy Arial, Courier New và Times New
  Roman. Game xin một phông máy ảo không có (Segoe UI, MS Gothic, tên tự đặt…) thì DirectWrite thay bằng Tahoma, và
  Tahoma là phông cứu cánh cuối. Tahoma không mở được, nên Direct2D bỏ cả đoạn chữ.
- **Cách sửa:** trước mỗi lần chạy Wine, mọi mục trong khoá phải có file:
  - Phông riêng của Wine (`share/wine/fonts` của gói Wine đang dùng) được chép vào `C:\windows\Fonts` đúng tên mục ghi
    (`wingdings.ttf` lấy từ `wingding.ttf`).
  - File có sẵn dưới đuôi khác thì mục ghi file đó (`msyh.ttf` → `msyh.ttc`).
  - Phông có trong imagefs thì mục trỏ tới đó (`Z:\usr\share\fonts\truetype\ms\simsun.ttc`, `malgun.ttf`,
    `Nirmala.ttf`), không chép (18 MB và 13 MB).
  - Mục không có file ở đâu cả thì bỏ (`msgothic.ttc`, `micross.ttf`, `serife.fon`, các đường dẫn `Z:\system\fonts\…`
    và `Z:\data\data\com.termux\…` của máy dựng prefix).
  - Phông trong `C:\windows\Fonts` chưa có mục thì được thêm mục theo tên đầy đủ của phông.
- **Tiếng Việt:** Tahoma của Wine thiếu 95 trong 134 chữ Việt có dấu, mà DirectWrite dùng Tahoma cho mọi chữ của phông
  thiếu. App làm từ DejaVu Sans của imagefs (đủ 134 chữ) bộ phông `C:\windows\Fonts\agvn\tahoma.ttc` và
  `tahomabd.ttc`, mỗi bộ gồm "Tahoma" và "Segoe UI" (thường, đậm), dùng chung hình chữ. Hai mục Tahoma của khoá trỏ
  tới đó. GDI (chữ của hộp thoại và game cũ) không đọc thư mục con `agvn`, và bỏ qua mục vì nó đã có Tahoma của Wine
  trong `C:\windows\Fonts`, nên chữ GDI vẫn là Tahoma của Wine. Tahoma thật của người chơi chép vào (khác file của
  Wine) thì được giữ cho DirectWrite.
- **Wine tự sửa khoá:** khi Wine mở, nó xoá khỏi khoá các phông ngoài ghi trong `HKCU\Software\Wine\Fonts\External
  Fonts` mà nó không thấy ở chỗ cũ. App bỏ khỏi danh sách đó các tên nay đã nằm trong `C:\windows\Fonts`
  (Tahoma, Marlett…), để Wine không xoá mục của chúng. Lần cập nhật prefix đầu tiên của Proton 10 có thể ghi lại mục
  Tahoma cũ; lần mở sau app sửa lại.
- **Cái giá:** lần đầu mở mỗi container mất khoảng 0,2 giây và thêm khoảng 1,8 MB (hai bộ phông, phông Wine); các lần
  sau dưới 0,02 giây. Chữ DirectWrite xin Tahoma hay Segoe UI có dáng DejaVu Sans (rộng hơn Tahoma một chút).
- **Còn thiếu:** chữ Nhật, chữ Trung của DirectWrite vẫn phụ thuộc phông dự phòng "Noto Sans CJK" (Wine tìm trong
  phông của Android). Game Nhật vẽ kana bằng DirectWrite mà vẫn ra ô vuông thì cần nhật ký của game đó.
- **Nhật ký:** `su-kien.txt` có `Phông DirectWrite: chép …; ghi …; lấy … trong app; bỏ mục không có file: …; Tahoma,
  Segoe UI đủ chữ Việt cho DirectWrite (từ DejaVu Sans); thêm … phông có sẵn vào danh sách`, chỉ ở lần mở có thay đổi.

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

## Toàn màn hình cho mọi game

Từ bản 0.1.39 (`AgvnFullscreen`). Anh Sơn yêu cầu ngày 11/10/2026: một nút cho mọi game chơi trong app hiện kín màn
hình. Anh chọn kiểu "kéo giãn đầy màn" (hình bè ngang một chút, không mất phần nào của khung game), tắt sẵn.

- **Ở đâu:** Cài đặt → "MÀN HÌNH GAME" → "Toàn màn hình cho mọi game" (`agvn_fullscreen_all`), tắt sẵn. Game đang
  chạy tốt không đổi gì nếu người chơi không bật.
- **Thứ tự:** lựa chọn riêng của game ("Kéo giãn toàn màn hình" trong cài đặt game, `fullscreenStretched` = "1" hay
  "0") thắng; game chưa chọn thì theo môi trường chạy (container), rồi tới công tắc chung. Ô của game hiện đúng trạng
  thái đang áp; người chơi đổi ô đó thì game có lựa chọn riêng từ đó.
- **Từng cách chạy:**
  - Game Windows: kéo giãn màn hình X như ô "Kéo giãn toàn màn hình" sẵn có (hình và cú chạm).
  - RPG Maker XP/VX/VX Ace (mkxp-z): `fixedAspectRatio` = `false` trong `mkxp.json`.
  - RPG Maker MV/MZ (trang HTML): `html-compat.js` phần 7 kéo canvas, video, hộp lỗi kín trang; cú chạm đổi về toạ độ
    game theo từng chiều (`Graphics.pageToCanvasX/Y`). Thử: `node tools/agvn/tests/html_stretch_sim.js`. Game HTML
    khác (không phải RPG Maker) tự lo khung hình của nó.
  - Ren'Py: `main.py` của app đặt `config.adjust_view_size` thành cả màn hình, trừ khi game tự đặt. Đã đọc
    `renpy/gl2/gl2draw.pyx`: khi có hàm này, Ren'Py không chừa viền, vẽ và đổi toạ độ chạm theo từng chiều.
  - Godot: bản vá engine `0005` (`AGVN_GODOT_STRETCH`), xem `scripts/agvn/godot/README.md`.
- **Cái giá:** hình bị bè ngang theo tỉ lệ màn máy (ví dụ khung 16:9 trên màn 20:9 rộng thêm khoảng 25%). Vì vậy là
  công tắc người chơi tự bật, không bật sẵn.

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
- Game chạy Proton 10 vẫn đen khi "Đồng bộ khung hình" đã bật (hoặc không có gì khác để đề nghị): thanh đề nghị "Chạy
  bằng Wine cũ (Proton 9)" (từ bản 0.1.40, `AgvnFixes.wineOld`). Bấm thì game về container Proton 9 ở lần mở sau, cùng save
  trong thư mục Windows (`AgvnGameMove`), và Proton 10 không được đề nghị lại cho game đó. Phiếu ngày 12/10: Xung Đột Bại
  Đức (HaitokunoShoudou.exe, DirectX 11, khung 854×480 kín màn) trên Galaxy S20 FE (Adreno 650), bản 0.1.38, chạy Proton
  10: chỉ vẽ 2 khung rồi đen; bật đồng bộ khung hình, đổi Đồ họa, đổi DXVK 1.10.3 đều không giúp, và từ lần mở thứ hai
  thanh không hỏi gì nữa. Không đưa vào "Tự sửa lỗi" cho lỗi người chơi báo: vòng thử ở đó trả cấu hình về nếu không giúp,
  mà chuyển Wine thì không trả về được (`AgvnRepairTest`).
- Khung game lớn theo màn hình (xem trên) thì không được đề nghị màn hình cỡ khung nữa.
- Lúc app dừng game (người chơi rời app hoặc tắt màn hình, hay bấm nút tạm dừng ⏸ ở thanh bên), app không đọc hình,
  và khoảng đó không tính vào thời gian chờ và thời gian canh (`AgvnGamePause`). Trước đây, game đang đen lúc tải mà
  người chơi sang app khác thì quay lại có thể gặp ngay thanh "Màn hình game vẫn đen".

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
cho game (không có `EC_ERRORABORT` hay `EC_COMPLETE`), nên game chờ mãi.

Gốc lỗi nằm ở `mfplat` của Proton 9: `MFInitMediaTypeFromVideoInfoHeader` (`dlls/mfplat/mediatype.c`) chuyển kiểu phim
của DirectShow sang Media Foundation mà bỏ phần sau `VIDEOINFOHEADER`, nên `MF_MT_USER_DATA` trống. Proton 10 và 11
chép phần đó sang, nên phát được (GameHub dùng Proton 11). Vì vậy app có Wine thứ hai, Proton 10 (xem "Hai bản Wine"
bên dưới).

App canh lỗi này cho mọi game (`AgvnMovieWatch`, `AgvnMovieRules`):

- Tắt "Bật debug Wine" thì GStreamer không in gì (winegstreamer chỉ đặt `GST_DEBUG` khi kênh gstreamer của Wine bật).
  App đặt `GST_DEBUG=WINE:2,videodecoder:2`: chỉ cảnh báo của bộ giải mã phim và của winegstreamer. Người chơi tự đặt
  `GST_DEBUG` trong biến môi trường thì app giữ của người chơi.
- Từ 100 dòng từ chối trở lên trong 5 giây, kéo dài ít nhất 1 giây: phim không phát được. `su-kien.txt` ghi "Phim
  trong game không phát được: WMV3 1280x720 …" (định dạng đọc từ dòng caps Wine in ra), và dòng trên góc phải đổi
  thành "Phim trong game không phát được (0:12)".
- Game đang chạy Proton 9: thanh có thêm "Chạy lại bằng Wine mới" (đứng đầu). Bấm thì app cài Proton 10 (chỉ lần đầu,
  dòng góc phải ghi "Đang cài Wine mới (Proton 10), chỉ lần đầu: …%"), rồi game tắt và tự mở lại trên Proton 10, save
  trong thư mục Windows đi theo (xem "Hai bản Wine"). Game đã từng hỏng trên Proton 10 và được đưa về thì không có nút
  này nữa.
- Thanh "Phim trong game không phát được" có "Bỏ qua phim" và "Chờ thêm". "Bỏ qua phim" nhấp chuột giữa khung game;
  2,5 giây sau phim vẫn kẹt thì gửi phím Esc. `su-kien.txt` ghi "Bỏ qua phim: được, bằng nhấp chuột" (hoặc "phím Esc"),
  hoặc "Bỏ qua phim: không được". Không được thì lối tắt nhớ (`agvnMovieSkip`: 0), lần sau thanh chỉ báo lỗi, không
  đề nghị bỏ qua nữa.
- Phim ngừng lỗi 10 giây rồi một phim khác lỗi: app báo lại.

Nhật ký Wine không còn phình vì dòng lặp (`AgvnLogRepeats`): dòng nào đã ghi trong 5 giây qua (bỏ qua giờ, luồng và
địa chỉ `0x…`) thì không ghi lại, mỗi giây ghi một dòng "[AGVN] ×N nữa: …". Quá 64 MB thì ngừng ghi, kèm một dòng báo.
300 dòng cuối của Wine (`wine-cuoi.txt`, và chỗ "Tự sửa lỗi" đọc) cũng gộp dòng lặp ("[AGVN] ×N dòng lặp lại"), nên
phim lỗi không đẩy các dòng khác ra ngoài. Chỉ gộp khi giữa hai dòng được ghi có hơn 4 dòng lặp; từ 4 dòng trở xuống thì
ghi đủ. Bản 0.1.21 gộp cả một dòng: danh sách extension Vulkan của DXVK (mỗi extension kèm dòng "extension supported :
1") thành ra "VK_EXT_hdr_metadata", "[AGVN] ×1 dòng lặp lại". Dòng lặp ở cuối lần chơi cũng được ghi.

## Hai bản Wine: Proton 9 và Proton 10

App có hai bản Wine. Mỗi bản có môi trường chạy (container) riêng, vì Wine gắn với container:

- **Proton 9** (`proton-9.0-arm64ec`, có sẵn từ trước): container đầu tiên. **Game mới thêm vào đây theo mặc định**
  (`AgvnImportDialog`, `AgvnWine10.forImport`; quyết định của anh Sơn ngày 15/10/2026, từ bản 0.1.45). Game .NET cũng
  cần Proton 9 (Proton 10 cần Wine Mono 10.4.1, app chỉ có 9.3.1).
- **Proton 10** (`Proton-10.0-4-arm64ec-9`, `AgvnWine10`): container "AGVN Proton 10". Nó phát được phim WMV3 mà Proton
  9 không phát được, nên app chỉ đề nghị khi phim của game không chạy ("Chạy lại bằng Wine mới", `AgvnMovieWatch`).
  - Từ 0.1.21 đến 0.1.44, game mới vào Proton 10 theo mặc định. Game đã thêm trong thời gian đó vẫn ở Proton 10; app
    không tự chuyển game đang chạy tốt. Game lỗi trên Proton 10 vẫn được "Tự sửa lỗi" và thanh màn hình đen đề nghị
    "Chạy bằng Wine cũ (Proton 9)".
  - Gói `proton-10.0-4-arm64ec.wcp` nằm trong APK (thêm khoảng 114 MB). Lần đầu cần (bấm "Chạy lại bằng Wine mới"), app
    giải nén vào `files/contents/Proton/10.0-4-arm64ec-9` như một gói `.wcp` người chơi tự cài, rồi tạo container "AGVN
    Proton 10". Mất khoảng 1 phút, cần trống khoảng 900 MB. Dòng góc phải báo tiến độ.
  - App bỏ các file không dùng tới: thư viện tĩnh `.a` (không thì mỗi container chép thêm vào `system32`), và các thư
    viện Wayland/Mesa trong `lib/` cùng `share/vulkan` (app vẽ qua X11, `lib/` không nằm trong đường dẫn thư viện). Còn
    khoảng 394 MB thay vì 553 MB.
  - Không cài được (thiếu chỗ) thì game mới vào Proton 9 như trước.
- **Chuyển game giữa hai bản** (`AgvnGameMove`): app ghi `agvnMoveTo` vào lối tắt, và lần mở sau, trước khi đọc game,
  app chuyển lối tắt sang container kia: file lối tắt, biểu tượng, save trong thư mục Windows (`AgvnSaveLocations`,
  phần `profile/`) và PlayerPrefs của Unity (`AgvnSavePrefs`). Thư mục game không đổi, save nằm trong đó giữ nguyên. Chuyển
  lúc mở chứ không lúc đang chơi, vì game đang chạy ghi lại cài đặt vào file của nó khi tắt.
- **Bản mới của một game đã có** (`AgvnGameVersions`, từ bản 0.1.33). Phiếu ngày 08/10/2026: khách nhập bản mới AGVN
  phát hành của một game (thư mục mới, như `…-v0.7.6-VIET-…`) thì mất save, và lần mở đầu tải rất lâu.
  - Vì sao: thư viện chỉ nhận ra một game qua thư mục của nó. Bản mới bị coi là game mới nên vào container Proton 10,
    còn bản cũ ở container Proton 9. Save trong thư mục Windows của container cũ (Unity `LocalLow` và PlayerPrefs, Unreal
    `SaveGames`, Ren'Py `AppData`) không có ở container mới. Container đó cũng chưa có cache shader của game.
  - Cách sửa: khi thêm game, nếu thư viện có bản khác của cùng game thì bản mới vào đúng container của bản cũ. Save
    ở đó dùng chung luôn, như cài bản mới trên PC. Save nằm trong thư mục game cũ (RPG Maker, Ren'Py, KiriKiri...)
    được chép sang thư mục mới, nhưng chỉ khi chỗ đó ở bản mới chưa có save nào; save của bản mới không bao giờ bị
    ghi đè. Thông báo sau khi thêm: "Đây là bản mới của …: game dùng chung save với bản cũ."
  - Cùng game: trùng tên file exe, và với Unity thì trùng hãng và tên game trong `app.info` (Unity lưu save theo hai tên
    này). Game khác thì so tên game đã bỏ số phiên bản (`v0.7.5`, `1.044`). Số của phần tiếp theo ("Game 2") được giữ
    lại, nên không bị nhầm với phần trước.
  - Khách đã nhập bản mới trước khi có bản sửa này: save vẫn còn ở bản cũ. Trong thư viện, menu ⋮ của bản cũ →
    "Xuất save", rồi menu ⋮ của bản mới → "Nhập save" và chọn file .zip vừa xuất (có cả PlayerPrefs của Unity).
- **Đường lui:** game chạy Proton 10 mà "Game bị lỗi và tự tắt" hoặc "Game tắt ngay sau khi mở": "Tự sửa lỗi" có "Chạy
  bằng Wine cũ (Proton 9)". Bấm thì game về Proton 9 ở lần mở sau, và Proton 10 không được đề nghị cho game đó nữa
  (`agvnNotWine10`).
- Chưa thử trên máy: Proton 10 cần FFmpeg 8 cho `winedmo` (bộ đọc AVI, WAV, MP3 mới qua Media Foundation), imagefs chỉ
  có FFmpeg 7.1. `winedmo` không nạp được thì chỉ báo `Failed to init unixlib`, game không sập. Phim MP4 vẫn qua
  winegstreamer như Proton 9.

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

## Nhật ký: game gì, có mod không

Từ bản 0.1.27, dòng đầu của `su-kien.txt` nói game nào đang chạy (`AgvnGameFacts`): file exe, engine app nhận ra, các
file `.dll` cạnh exe và thư mục mod (`BepInEx`, `MelonLoader`, `ue4ss`). Ví dụ: `Game: Rina.exe · engine UNITY · DLL
cạnh exe: GameAssembly.dll, UnityPlayer.dll, winhttp.dll · mod: BepInEx`. App cũng chép nhật ký của các bộ nạp mod:
`BepInEx/LogOutput.log`, `MelonLoader/Latest.log`, `ue4ss/UE4SS.log` (cạnh exe), như nhật ký engine.

Lý do: hai nhật ký gửi về ngày 07/10/2026 (máy HONOR AAK-AN00) không đủ để biết game bị gì. Rina có `Player.log` của
Unity trống cả 5 lần chạy; chỉ biến `WINEDLLOVERRIDES` trong `moi-truong.txt` cho thấy cạnh exe có `winhttp.dll` (bộ
nạp BepInEx), mà nhật ký của BepInEx thì app chưa chép. Isekai NTR Inn không có nhật ký engine nào và không biết là
engine gì. Máy HONOR cũng không cho app đọc logcat (`logcat.txt` chỉ có một dòng mã hoá).

Từ bản 0.1.29, game app chưa biết engine, hay game có mod, có thêm các dòng sau dòng đầu:
- `Thư mục game: …`: mọi file và thư mục ở thư mục game, trừ `.dll` (đã có ở dòng đầu); file từ 1 MB có kèm cỡ;
- `Thư mục BepInEx: …` (hay `MelonLoader`, `ue4ss`): những gì thư mục mod có, kèm cỡ mọi file (`LogOutput.log
  trống`: BepInEx chưa ghi được gì);
- `File exe: …` (chỉ game chưa biết engine, `AgvnPeFacts`): 32 hay 64 bit; các DLL Windows file exe nạp (Direct3D 9
  hay 11, OpenGL, `quartz`/`mfplat` cho phim, `dsound`...); đóng gói (Enigma Virtual Box giữ cả game trong exe;
  SteamStub `.bind` cần Steam); dữ liệu nối sau exe (zip của LÖVE hay NW.js, PyInstaller, bộ cài NSIS...).

Từ bản 0.1.37, dòng thứ hai của `su-kien.txt` là đường dẫn app mở game từ đó: `File chạy:
/storage/emulated/0/…/Lifeguard Holic.exe`. Dòng này có thêm `KHÔNG có file này trên máy` khi app không thấy file ở đó,
hoặc `ổ đĩa không rõ, app không kiểm được file` khi đường dẫn dùng ổ đĩa Windows mà container không có. Đường dẫn có chữ
ngoài ASCII (chữ có dấu, chữ Nhật...) thì dòng ghi các chữ đó: `winhandler.exe` của Winlator làm mất các chữ này, nên
game đó mở bằng `agvn-winhandler.exe` (xem "Thư mục game có dấu"). Lý do: ngày 10/10/2026, game "GAMEHUB" (Lifeguard
Holic, Unity) trên Xiaomi 23090RA98G hiện hộp "File not found." ngay khi mở, trong khi GameHub mở được game. Game chưa
hề chạy: không có nhật ký Unity, không có `AGVN-cheat.log`. Logcat lúc mở game đã mất vì người chơi vuốt tắt app, nên
nhật ký không cho biết app tìm file ở đâu.

Khi game Windows kết thúc, `su-kien.txt` có thêm dòng `Phút cuối của game (mỗi 5 giây): FPS …; luồng bận nhất (% một
nhân) …; GPU (%) …` (`AgvnSlowWatch.lastMinute`). App vốn đo các số này mỗi 5 giây để biết game chậm; giờ app đo suốt
cả phiên (trước đây dừng sau lần hỏi "Game chậm") và ghi phút cuối. Nếu cuối phút đó game không vẽ khung nào, dòng
có thêm "không vẽ khung nào trong N giây cuối". Đọc dòng này:
- FPS vẫn đều: game vẫn vẽ; cảnh không đi tiếp (đợi phím, đợi chạm, kẹt logic);
- FPS 0, một luồng bận gần 100%: game đang làm việc mà không vẽ (đang tải, tạo file lần đầu, hay vòng lặp);
- FPS 0, không luồng nào bận: game đang đợi một thứ không đến (đứng hẳn).

FPS ở dòng này đếm cả khung hình game vẽ vào cửa sổ lẫn khung hình Vulkan, OpenGL game đưa ra qua Present (DXVK,
VKD3D, Godot, NW.js...). Trước bản 0.1.41 chỉ có loại đầu được đếm, nên game DXVK chạy đều vẫn bị ghi "FPS 0 0 0;
không vẽ khung nào". Dòng "Đang khởi động" cũng vậy: nó chỉ tắt khi game vẽ vào cửa sổ, nên game DXVK đã vào menu vẫn
còn dòng này. Ví dụ ngày 14/10/2026: Legend Cleaner (Unreal) trên LG LM-V510N (Adreno 640) lần đầu mở mất hơn 1 phút
ở màn hình đen, rồi menu chạy 25 FPS mà dòng vẫn ghi "Đang khởi động 3:16". Từ bản 0.1.41 cả hai đếm khung hình đưa
ra qua Present (`AgvnInputHold.onGameFrame`).

Lý do (bản 0.1.29): nhật ký thứ hai của Isekai NTR Inn và Rina (bản 0.1.28, cùng máy HONOR AAK-AN00, 07/10/2026) vẫn
không cho biết game bị gì. Cả hai đều thử đủ 3 cách, mỗi cách chạy thật 1–2 phút (sửa lỗi của bản 0.1.27 đã có tác
dụng), rồi người chơi vẫn báo đứng hình.
- **Isekai NTR Inn:** vẫn `engine UNKNOWN`, không có DLL cạnh exe, không có nhật ký engine; Wine không báo lỗi nào.
- **Rina:** `Player.log` của Unity trống cả 4 lần, và BepInEx chưa hề tạo `LogOutput.log`. Nhưng Wine báo
  `find_forwarded_export module not found for forward 'icuuc68.u_charsToUChars_68' used by …\icu.dll` ngay lúc mở game.
  Đó là .NET đang nạp ICU, nên runtime .NET mà BepInEx bản IL2CPP kèm theo đã chạy. Lỗi này không làm game dừng: .NET
  không có ICU thì dùng cách khác. Vậy game kẹt khi BepInEx đang khởi động, trước khi BepInEx và Unity ghi được dòng
  nhật ký nào. Vì thế mới có cách "Chạy không có mod".
- Cả hai: từ giây thứ 10, máy giới hạn nhân CPU mạnh nhất còn 45–66% (headroom 0,80–0,86).

Game HTML RPG Maker MV/MZ có ảnh mã hoá (`.rpgmvp`, `.png_`) bị thiếu, hoặc ngắn hơn 32 byte (16 byte đầu "RPGMV" và
16 byte khoá che): app đưa một ảnh trống, mã hoá bằng khoá của game (`data/System.json`), như ảnh `.png` bị thiếu
(`AgvnHtmlStandIn`, bản 0.1.36). Trước đó, file rỗng làm game dừng với "RangeError: Invalid typed array length: 16" ở
`Decrypter.decryptArrayBuffer` (Yarisutemesubuta trên OPPO CPH2127, 10/10/2026), còn file thiếu làm game dừng ở
"Failed to load". Tên các ảnh đó (5 ảnh đầu) nằm trong `app/chay-nhe.txt` của "Gửi nhật ký", cùng câu lỗi đầu tiên
của game, vì logcat của máy bận có khi chỉ giữ được vài chục giây.

Game HTML (RPG Maker MV/MZ, Tyrano): một thông báo của trang lặp lại chỉ được ghi vào logcat lần đầu, rồi lần thứ 10,
100, 1000… kèm số lần (`AgvnHtmlConsole.repeat`, bản 0.1.28). Lý do: nhật ký Train45 gửi ngày 07/10/2026 có `logcat.txt`
toàn một dòng của một game RPG Maker khác chạy trước đó ("The provided value 'undefined' is not a valid enum value of
type CanvasTextAlign": plugin vẽ chữ không kèm cách canh), 19.339 lần trong 9 phút, nên logcat không còn gì khác.

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
- [ ] Đang chơi một game Windows (game không crash), vuốt AGVN khỏi danh sách app gần đây rồi mở lại app: không có hộp
      hỏi; `tom-tat.txt` của phiên đó ghi "Người chơi vuốt tắt AGVN…".
- [ ] Game vẽ chữ bằng DirectWrite (nạp `dwrite.dll`, `d2d1.dll`) trong container tạo trước bản 0.1.40: mở game,
      menu và hộp thoại có chữ, chữ Việt đủ dấu (ẳ ẵ ặ ữ ự). `su-kien.txt` có dòng `Phông DirectWrite: …`; nhật ký Wine
      không còn `Failed to open … tahoma.ttf` hay `Failed to create last resort font`. Mở lần hai: không còn dòng
      `Phông DirectWrite`.
- [ ] Game cũ vẽ chữ bằng GDI (hộp thoại Wine, game RPG Maker 2003…): chữ vẫn đủ, không đổi sang dáng DejaVu Sans.
- [ ] Game Windows: "✕ Thoát" trên thanh ⌨ ✎ 👁 ⛶ hiện thanh "Thoát game?"; "Chơi tiếp" để game chạy tiếp, "Thoát game"
      đưa về thư viện và không có hộp hỏi.
- [ ] Game mở chậm (hơn 10 giây không vẽ, ví dụ game Unity trên máy yếu): lần mở sau, dòng trên góc phải ghi "Đang khởi
      động 0:25 · lần trước 1:40".
- [ ] Bản 0.1.41, game DXVK mở chậm (như Legend Cleaner lần đầu): dòng "Đang khởi động" mất trong vài giây sau khi
      game vào menu. Thoát game: `su-kien.txt` có `Phút cuối của game … FPS` gần mức FPS của HUD, không có "không vẽ
      khung nào" khi game vẫn chạy.
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
- [ ] Thêm game mới (không phải .NET) lần đầu: hộp "Đang chuẩn bị Wine mới (Proton 10) cho game, chỉ lần đầu: …%" chạy
      khoảng 1 phút, rồi game nằm trong thư viện; Cấu hình → Môi trường chạy ghi "AGVN Proton 10". Thêm game thứ hai:
      không có hộp đó nữa. `adb logcat -s AGVN` có `Proton 10 installed` và `Proton 10 container …`.
- [ ] Game KiriKiri ở phiếu 06/10 (phim WMV3) thêm mới vào Proton 10: phim mở đầu phát có hình có tiếng, hết phim vào
      màn tiêu đề; nhật ký Wine không có `Subclass refused caps`.
- [ ] Cùng game đó nhưng đã thêm từ trước (Proton 9): thanh phim lỗi có "Chạy lại bằng Wine mới". Bấm: dòng góc phải báo
      tiến độ cài, game tắt rồi tự mở lại; `su-kien.txt` phiên đầu có "Game chuyển sang Wine mới (Proton 10) khi mở
      lại"; lần này phim phát. Save trong thư mục game vẫn còn.
- [ ] Một game Unity có phim MP4, một game Ren'Py và một game DXVK thêm mới vào Proton 10: vào menu, phim phát, FPS như
      trên Proton 9 (so với bản cũ của chính game đó).
- [ ] Thêm một game .NET: vào Proton 9 (container đầu tiên), không có hộp chuẩn bị Wine mới.
- [ ] Game trên Proton 10 tự tắt ngay khi mở: "Tự sửa lỗi" có "Chạy bằng Wine cũ (Proton 9)". Bấm, "Mở lại game ngay":
      game về container đầu tiên; lần sau phim lỗi thì thanh không còn "Chạy lại bằng Wine mới".
- [ ] Máy còn trống dưới 900 MB, thêm game mới: game vào Proton 9 như trước, `adb logcat` có `Proton 10 not installed: …
      MB free`.
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
- [ ] Đang chơi game Windows, sang app khác (hoặc tắt màn hình) khoảng 1 phút rồi quay lại: `su-kien.txt` có "Rời app
      (…): game dừng tới khi quay lại" rồi "Game chạy tiếp sau 60 giây dừng (không tính là đứng hình)", không có dòng
      "Đứng hình" cỡ 60000 ms. Bấm nút tạm dừng ⏸ ở thanh bên rồi bấm lại: `su-kien.txt` có "Bấm nút tạm dừng (⏸) ở
      thanh bên: game dừng" và "Game chạy tiếp sau … giây dừng".
- [ ] Mở game còn đen lúc tải, sang app khác 1 phút rồi quay lại: không có thanh "Màn hình game vẫn đen" ngay khi quay
      lại.
- [ ] Game DXVK: `wine-cuoi.txt` có đủ danh sách extension Vulkan (dưới mỗi extension có dòng "extension supported"),
      không có "[AGVN] ×1 dòng lặp lại".
- [ ] Máy GPU PowerVR (driver thiếu tính năng DirectX 11), game Unity DirectX 11: hộp hỏi "Driver đồ họa thiếu tính năng
      DirectX 11 mà game cần", không có nút đổi bản DXVK. Bấm "Dùng WineD3D…" mà game vẫn hỏng: lần sau hộp hỏi "Máy
      không chạy được OpenGL với driver đồ họa này" với "Dùng lại DXVK (bỏ WineD3D)". Game DirectX khác trên máy đó không
      còn được đề nghị WineD3D; `adb logcat -s AGVN` có `OpenGL (Zink) does not start with the System driver`.
- [ ] Máy Adreno hoặc Mali: game DirectX và game OpenGL vẫn chạy như trước, `wine-cuoi.txt` không có `failed to load
      driver: zink`.
- [ ] Game Unity crash mà đứng hình (như Become A Vtuber trên Proton 10, máy Adreno 620): vài giây sau crash có thanh
      "Game bị lỗi và đã dừng" ghi chỗ crash (`UnityPlayer.dll+0x8d8b1a`). 10 giây sau game tự đóng; bấm "Đóng game
      ngay" thì đóng luôn. Về thư viện có hộp "Game bị lỗi và đứng hình", nút đầu "Giả lập CPU ổn định". `su-kien.txt`
      có "Unity báo crash trong …"; `tom-tat.txt` ghi "Game bị lỗi (crash) – Unity báo crash trong …"; có
      `unity-crash.txt` nếu Unity kịp ghi báo cáo.
- [ ] Cùng game đó, rời app ngay lúc crash: game không bị đóng khi bạn đang ở ngoài app. Quay lại thì thanh vẫn đó và
      10 giây được đếm từ lúc quay lại.
- [ ] Cùng game đó, vuốt tắt AGVN khi game đang đứng hình rồi mở lại app: có hộp "Game bị lỗi và đứng hình";
      `tom-tat.txt` ghi "Game bị lỗi (crash) – Unity báo crash trong …; sau đó: …".
- [ ] Bấm "Giả lập CPU ổn định" rồi "Mở lại game ngay": game vào được thì báo AGVN cách đó. Vẫn crash thì lần sau hộp
      hỏi có "Chạy bằng Wine cũ (Proton 9)", không hỏi lại giả lập CPU.
- [ ] Game Unity chạy bình thường vài phút rồi thoát: không có thanh "Game bị lỗi và đã dừng", không có hộp hỏi.
- [ ] Menu ⋮ của game ở thư viện có "Tự sửa lỗi" cạnh "Gửi nhật ký"; thanh trong game có "🩺 Tự sửa". Bấm: hộp "Game
      đang bị gì?" có 11 lỗi (từ bản 0.1.28 có "Hoạt ảnh, video hoặc hiệu ứng không chạy").
- [ ] Game Godot đã chuyển sang "Chạy bằng Windows" mà Godot 4.7 đọc được: chọn "Game chậm, giật, lag" hoặc "Hoạt ảnh,
      video hoặc hiệu ứng không chạy": cách đầu là "Chạy nhẹ: chạy game Godot thẳng trên Android". Thử: game mở bằng
      Godot cho Android, save vẫn còn. "Vẫn còn lỗi": game về lại Windows.
- [ ] Game Godot đang Chạy nhẹ, chọn "Hình có đốm đen…": cách đầu là "vẽ bằng OpenGL" (game dùng Vulkan) hoặc "vẽ bằng
      Vulkan" (game dùng Compatibility). `adb logcat -s AGVN` có `--rendering-driver` đúng cách đó.
- [ ] Game DXVK trên máy Turnip, chọn "Hình chập chờn, nhấp nháy": danh sách chỉ có các cách đổi được gì đó (game
      không bật async thì không có "Tắt DXVK async"). Bấm "Thử cách 1": game mở lại; `su-kien.txt` có "Tự sửa lỗi:
      người chơi báo …, thử: …".
- [ ] Chơi khoảng 45 giây: thanh "Còn lỗi … không?". "Vẫn còn lỗi": cấu hình về như trước (xem trong "Cấu hình" của
      game), app đặt cách tiếp và hỏi "Mở lại game ngay". "Hết lỗi rồi": thông báo "Đã giữ cách sửa: …", lần mở sau
      không hỏi nữa.
- [ ] Đang thử một cách sửa cho "Game đứng hình, treo", game đứng yên ở màn tải (như Immoral Bathhouse sau logo): thanh
      hỏi chưa hiện; game tải xong và vẽ lại thì thanh mới hỏi. Game đứng yên 3 phút liền thì thanh hỏi.
- [ ] Sang app khác ngay sau khi game mở rồi quay lại: thanh hỏi chỉ hiện sau 45 giây chơi thật (không tính lúc ở
      ngoài app).
- [ ] Thử đến hết cách: thanh "Đã thử hết cách trong app", cấu hình của game đúng như trước lần thử đầu, có nút "Gửi
      nhật ký".
- [ ] Đang thử một cách, ở thư viện bấm "Tự sửa lỗi": hộp hỏi "App đang thử cách sửa: … Lỗi còn không?" với "Hết lỗi
      rồi", "Vẫn còn lỗi", "Chọn lỗi khác". "Chọn lỗi khác" trả cấu hình về như trước rồi mới hỏi lỗi mới.
- [ ] Trong game, "Vẫn còn lỗi" rồi đừng bấm "Mở lại game ngay"; bấm "🩺 Tự sửa": hộp nói game chưa chạy lại với cách
      mới, chỉ có "Mở lại game ngay" và "Chọn lỗi khác". Bấm "Mở lại game ngay": game mở lại, 45 giây sau mới hỏi.
      Ở thư viện cũng vậy, với nút "Chơi thử với cách này".
- [ ] `su-kien.txt` của mọi phiên chơi game Windows bắt đầu bằng dòng "Game: <exe> · engine … · DLL cạnh exe: …". Game
      có BepInEx: dòng đó có "mod: BepInEx" và thư mục phiên có `LogOutput.log`.
- [ ] Bản 0.1.29, game có BepInEx (Rina): `su-kien.txt` có `Thư mục game: …` và `Thư mục BepInEx: …`. Chọn "Game đứng
      hình, treo": cách thứ hai là "Chạy game không có mod (BepInEx)…". Thử: `moi-truong.txt` của lần chạy đó có
      `winhttp=b`; game chạy không có mod. "Vẫn còn lỗi": lần sau lại `winhttp=n,b` (mod chạy lại).
- [ ] Bản 0.1.29, game chưa biết engine (Isekai NTR Inn): `su-kien.txt` có `Thư mục game: …` và `File exe: …`.
- [ ] Bản 0.1.30, máy Mali, một game nặng (Unreal như Thanh Âm Mùa Hạ): khi driver báo `TILER_HEAP_OOM` nhiều lần,
      thanh "GPU hết bộ nhớ dựng hình" hiện trong game với nút hạ Đồ họa; `su-kien.txt` có `GPU hết bộ nhớ dựng hình
      (Mali, TILER_HEAP_OOM) N lần`. Lần mở sau game dùng mức tiết kiệm RAM cao nhất. Game chạy mượt không bị hỏi.
- [ ] Bản 0.1.30, Cài đặt → cài một bản DXVK, VKD3D hay Box64 đã có sẵn trên máy, hoặc một gói hỏng: app không văng.
- [ ] Bản 0.1.31, Open At Nine: `su-kien.txt` có `Chạm gửi như chuột thật…: game Unity đọc chuột bằng Input System`.
      Chạm CÀI ĐẶT thì mở bảng cài đặt, CHƠI MỚI thì vào truyện, chạm màn hình thì sang câu thoại. Bản cheat ghi
      `AGVN-cheat.log` cạnh exe: có `-> Input moi THAY` từ cú chạm đầu, không có dòng
      `doi giao dien game sang chuot kieu cu`. Kéo thả (thanh trượt âm lượng) và giữ chạm vẫn đúng chỗ ngón tay.
- [ ] Bản 0.1.31, một game Unity khác có Input System (như bản GameHub Unity 6): bấm được nút menu bằng chạm.
- [ ] Bản 0.1.31, game RPG Maker, Ren'Py (qua Windows), KiriKiri và Unity kiểu cũ: `su-kien.txt` không có dòng "Chạm
      gửi như chuột thật"; bấm, kéo thả, giữ chuột như trước, không bị bấm đôi.
- [ ] Bản 0.1.31, "Tự sửa lỗi" → "Chạm, bấm không ăn" ở một game Windows bất kỳ: có "Gửi cú chạm như chuột thật";
      Open At Nine thì có "Gửi cú chạm như trước". Thử rồi chọn "Vẫn còn lỗi": cấu hình về như cũ.
- [ ] Bản 0.1.31, Mii Chan (Godot 4.5.1 có Spine): mở bằng Windows một lần, `su-kien.txt` có `Game Godot dùng Spine
      4.x.y: …`. Nếu là 4.2.x: "Tự sửa lỗi" có "Chạy nhẹ"; chạy nhẹ thì con gà và hiệu ứng tim (cảnh Spine) hiện và
      chuyển động, `godot.log` không có "Cannot get class 'SpineSkeletonDataResource'", vào được màn chơi. Nếu là bản
      khác (4.3...): app không đề nghị "Chạy nhẹ", hộp "Game Godot bị tắt khi vẽ hình" có Vulkan và Direct3D 11 (ANGLE).
- [ ] Bản 0.1.33, một game Unity đã chơi có save, thêm bản mới của nó ở thư mục khác: thông báo "Đây là bản mới
      của …"; bản mới cùng container với bản cũ, vào game thấy save cũ. Game RPG Maker MV: thư mục `www/save` của bản
      mới có các file save của bản cũ. Thêm một game khác hẳn: vẫn vào Proton 10 như trước.
- [ ] Bản 0.1.39, Cài đặt → "Toàn màn hình cho mọi game" bật: game Windows, RPG Maker XP/VX (Chạy nhẹ), RPG Maker
      MV/MZ, Ren'Py và Godot (Chạy nhẹ) đều kín màn hình, không viền đen; chạm vào nút ở mép phải và mép dưới vẫn trúng.
      Một game chọn tắt "Kéo giãn toàn màn hình" trong cài đặt game thì vẫn giữ tỉ lệ. Tắt công tắc: mọi game như cũ.
- [ ] Bản 0.1.40, game DirectX chạy Proton 10 đen màn hình: lần đầu thanh hỏi "Bật Đồng bộ khung hình"; bật mà vẫn đen
      thì lần sau thanh hỏi "Chạy bằng Wine cũ (Proton 9)". Bấm, mở lại: `moi-truong.txt` là Proton 9, save còn.
- [ ] Bản 0.1.39, game Unity lưu bằng PlayerPrefs: chơi, lưu, bấm "Thoát" ngay. `su-kien.txt` có `Thoát: game tự
      đóng sau … ms`; mở lại game thì save còn. Game KiriKiri hỏi khi đóng: app tắt game ngay, không treo.
- [ ] Bản 0.1.32, bàn phím Bluetooth hoặc bộ thu USB: `thiet-bi.txt` có dòng `Bàn phím, chuột, tay cầm ngoài: …`.
      Trong game Windows gõ chữ, mũi tên, Enter, Esc được; `su-kien.txt` có `Bàn phím "…": phím tới game`. Bàn phím mà
      dòng đó ghi "Android gọi là tay cầm" (như của Av Director Life) cũng vậy.
- [ ] Bản 0.1.32, tay cầm thật (Xbox, DualSense...): nút, D-pad và cần analog vẫn tới game như trước.
- [ ] Bản 0.1.32, chuột ngoài: `su-kien.txt` có `Chuột "…": app nhận cú bấm (app đang giữ chuột)`.
- [ ] Bản 0.1.33, chuột USB hoặc Bluetooth trong game Windows có bộ phím ảo đang hiện: di chuột thì con trỏ của game
      đi theo, bấm trái, bấm phải, lăn chuột được. Mở menu bên trái, đóng lại: chuột vẫn được. Tay cầm thật: cần analog
      vẫn tới bộ phím ảo như trước.
- [ ] Bản 0.1.33, game hiện hộp "Assertion failed!" nhắc `vkCreateShaderModule`, vuốt tắt AGVN (không bấm OK) rồi mở
      lại app: thư viện hỏi "Driver đồ họa bị lỗi khi game vẽ hình". Game chạy Proton 10 thì có thêm "Chạy bằng Wine
      cũ". Vuốt tắt một game đang chạy bình thường: không có hộp hỏi.
- [ ] Bản 0.1.33, game Unreal hiện hộp "A D3D11-compatible GPU (Feature Level 11.0, Shader Model 5.0) is required
      to run the engine.", bấm OK: thư viện hỏi "Game cần DirectX 11 mức 11.0". Game đang chạy WineD3D thì có nút
      "Dùng lại DXVK (bỏ WineD3D)".
- [ ] Bản 0.1.33, máy có driver Vulkan dưới 1.3 (như Galaxy M34): "Tự sửa lỗi" không có "Đổi DXVK sang bản 2.3.1". Máy
      Zink không chạy: sau một lần WineD3D lỗi (kể cả khi tự thoát game), không game nào được đề nghị WineD3D nữa.
- [ ] Bản 0.1.34, game DXVK: bấm ảnh game để mở trang game, chơi. Trong game "🩺 Tự sửa" → "Màn hình đen dù game vẫn
      chạy", thử tới khi app đặt "Dùng WineD3D", đừng bấm "Mở lại game ngay", thoát game. Ở trang game bấm tim, rồi
      mở "Cấu hình": DX Wrapper vẫn là WineD3D. Bấm "Chơi": `moi-truong.txt` có `WINE_D3D_CONFIG`, không có
      `DXVK_CONFIG`.
- [ ] Bản 0.1.34, đang thử "Dùng WineD3D", vào "Cấu hình" của game đổi DX Wrapper về DXVK rồi chơi: vài giây sau khi
      game mở có thanh "Chưa thử được: Dùng WineD3D …" với nút "Thử: …", không có thanh "Còn lỗi … không?";
      `su-kien.txt` có "Tự sửa lỗi: game chạy không có …". Bấm "Thử: …" rồi "Mở lại game ngay": game chạy WineD3D,
      45 giây sau app mới hỏi.
- [ ] Bản 0.1.34, game Unreal hiện hộp "GameThread timed out waiting for RenderThread after 120.00 secs" (máy chậm,
      lần tải đầu), bấm OK: thư viện hỏi "Game Unreal tự dừng vì chờ vẽ hình quá lâu" với nút "Cho game chờ vẽ hình lâu
      hơn…". Bấm: game mở lại, "Cấu hình" → "Nâng cao" → "Tham số chạy" có `-nothreadtimeout`, và game không còn tự
      dừng sau 120 giây.
- [ ] Bản 0.1.35, game Unreal chạy WineD3D với driver thiếu tính năng (như MK Days trên Mali-G610), hộp DirectX 11
      bằng tiếng Nhật ("D3D11 互換の GPU (機能レベル 11.0、…) が…"), bấm OK: thư viện hỏi "Game cần DirectX 11 mức 11.0"
      với "Dùng lại DXVK (bỏ WineD3D)", không phải "Game tắt ngay sau khi mở". Lần game lỗi sau đó (như hộp
      vkCreateShaderModule), không còn nút "Dùng WineD3D thay DXVK" cho game này.
- [ ] Bản 0.1.36, game Windows nhập bằng AGVN (giả lập cảm ứng bật), chuột USB hoặc Bluetooth: di chuột thì con trỏ
      hiện và đi theo; chạm màn hình bằng ngón tay thì con trỏ ẩn; di chuột lại thì hiện lại. Chơi chỉ bằng tay: không
      thấy con trỏ, như trước.
- [ ] Bản 0.1.36, game RPG Maker MV có một ảnh `.rpgmvp` rỗng (0 byte) hay bị xoá: tới chỗ game dùng ảnh đó, game không
      dừng, chỗ ảnh để trống; `logcat -s AGVN` có `HTML game picture … shown empty`; "Gửi nhật ký" có `app/chay-nhe.txt`
      với dòng `standIn=…` tên ảnh đó.
- [ ] Bản 0.1.37, mở một game Windows bất kỳ: `su-kien.txt` có dòng `File chạy: …` ngay sau dòng `Game: …`. Game nằm
      trong thư mục có chữ có dấu thì dòng đó có `chữ ngoài ASCII: …`; file chạy đã bị xoá thì có `KHÔNG có file này
      trên máy`.
- [ ] Bản 0.1.37, game Windows trong thư mục có dấu (như `Download/Lifeguard Holic Việt Hóa/GAMEHUB`): game mở được,
      không còn hộp "File not found."; `su-kien.txt` có `Đường dẫn game có chữ ngoài ASCII (…): mở game bằng
      agvn-winhandler.exe…`. Game trong thư mục không dấu: không có dòng đó, mở như trước.
- [ ] Bản 0.1.32, Điều khiển → mở màn gán phím của một thiết bị, tắt thiết bị rồi xoay máy: app báo thiết bị vừa
      ngắt kết nối, không văng.
- [ ] Bản 0.1.29, thoát một game Windows bất kỳ (từ menu, hoặc "Mở lại game ngay" của "Tự sửa lỗi"): gần cuối
      `su-kien.txt` có dòng `Phút cuối của game (mỗi 5 giây): FPS …`. Game chạy mượt thì FPS đều, không có "không vẽ
      khung nào". Rời app rồi thoát game từ thông báo: không có dòng này.
- [ ] Game Unity ở Đồ họa Siêu nhẹ, chọn "Game tự tắt, văng ra" (hoặc Unity tự bắt crash): có "Để game Unity tự chọn
      chất lượng hình". Thử: lần mở sau `adb logcat -s AGVN` có `Unity quality back to the game's own`.
- [ ] Game Nhật bị chữ ô vuông, chọn "Chữ lỗi, ô vuông, ký tự lạ": "Chạy game bằng tiếng Nhật" sửa được; game đã chạy
      tiếng Nhật thì không có cách đó.
- [ ] Một game khác không bị báo lỗi: cấu hình không đổi gì.
- [ ] Nếu gặp game Unity chỉ crash lúc thoát từ menu (hộp "Game bị lỗi và đứng hình" sau khi thoát): gửi nhật ký phiên
      đó (`Player.log` cho biết Unity ghi gì ngay trước `Crash!!!`), để app nhận ra crash lúc thoát và thôi hỏi.

## DLL ở bộ nhớ chung: "No access to memory location." (bản 0.1.42)

Báo cáo ngày 14/10/2026 (POCO F8 Pro, bản 0.1.32): một game RPG Maker VX Ace bản PC chuẩn ở
`/storage/emulated/0/Download/AGVN/…`, chạy bằng Windows (Proton 10, FEX), hiện hộp "No access to memory location." ở
cả hai lần mở. Log Wine (đã bật nhật ký):

```
warn:module:alloc_module disabling no-exec because of L"RGSS301.dll"
warn:virtual:map_file_into_view noexec filesystem, mapped 0x1440000-0x154a000 without PROT_EXEC
warn:seh:dispatch_exception EXCEPTION_ACCESS_VIOLATION exception (code=c0000005) raised
warn:module:process_attach Initialization of L"RGSS301.dll" failed
trace:msgbox:MSGBOX_OnInit L"No access to memory location.\r\n"
```

- **Hai bản Wine xử lý khác nhau.** Bộ nhớ chung của Android gắn `noexec`: không chạy mã thẳng từ file được. Đọc mã máy
  `ntdll.so` của hai gói trong app:
  - Proton 10 (`proton-10.0-4-arm64ec.wcp`) có đoạn sửa riêng, không có trong Wine gốc: khi ánh xạ chung (`MAP_SHARED`)
    của một file cần quyền chạy bị từ chối, nó ánh xạ lại file mà bỏ quyền chạy (`noexec filesystem, mapped … without
    PROT_EXEC`).
  - Proton 9 (Wine 9.0) chép file vào bộ nhớ thường rồi cấp quyền chạy, cho mọi ánh xạ (`failed to set PROT_EXEC on
    file map, noexec filesystem?`).
  - `RGSS301.dll` không có cờ NX, nên từ lúc nạp nó, Wine xin quyền chạy cho mọi vùng nhớ đọc được. Ở Proton 10, vùng
    ánh xạ từ file ở `Download` không có quyền đó, và Wine không đổi được quyền của vùng này nữa. Chưa thử trên máy
    xem Proton 9 có chạy được game này không.
- **App làm gì (`dll-no-access`):** hộp đó hiện thì "Tự sửa lỗi" đề nghị, theo thứ tự:
  1. "Chạy nhẹ" (fix `rgss-light`), cho game RPG Maker XP/VX/VX Ace đang chạy Windows. mkxp-z chạy game thẳng trên
     Android, không qua Wine. Fix này cũng đứng đầu "Game crash" và "Game tắt ngay, không rõ lỗi" của game RPG Maker
     XP/VX/VX Ace đang chạy Windows (bản 0.1.41 trở về trước, Chạy nhẹ văng ngay ở mọi game loại này, nên có người
     chơi đã chuyển game sang Windows).
  2. "Chạy bằng Wine cũ (Proton 9)", cho game đang chạy Proton 10.
  3. Gửi nhật ký.
  Như mọi cách sửa, cách nào đã thử mà không hết lỗi thì không đề nghị lại.
- **Proton 9 cũng không nạp được (báo cáo ngày 21/10/2026, POCO F8 Pro):** SELinux cấm chạy mã từ file ở
  `/storage/emulated` (`avc: denied { execute } … RGSS301.dll … fuse`). Wine báo `disabling no-exec because of
  L"RGSS301.dll"`, rồi `DllMain` của `RGSS301.dll` gặp lỗi bên trong `libwow64fex.dll` (giả lập 32-bit FEXCore) và
  `Initialization of L"RGSS301.dll" failed`. Vì vậy với game RPG Maker XP/VX/VX Ace, Chạy nhẹ là đường chính.
- **Chưa làm:** sửa trong Wine của Proton 10 (cho ánh xạ đó chép file vào bộ nhớ như Proton 9) cần dựng lại gói Proton.
  Chép `Game.exe` và `System/*.dll` vào bộ nhớ riêng của app rồi chạy từ đó (đề xuất của báo cáo 21/10/2026) cần giữ
  thư mục làm việc và chỗ lưu save ở thư mục game; chưa làm vì chưa thử được trên máy. Thử WOWBox64 thay FEXCore (cài
  đặt game → "Giả lập 32-bit") cho trường hợp `DllMain` lỗi trong FEX.
- **Test:** `AgvnDoctorTest.aDllThatCannotRunFromSharedStorage`.
- **Thử máy:**
  1. Game VX Ace của báo cáo, đang chạy bằng Windows (Proton 10): hộp "No access to memory location." hiện, tắt game,
     về thư viện. "Tự sửa lỗi" hỏi "Game không mở được thư viện DLL của nó", nút đầu là "Chạy nhẹ: chạy game RPG
     Maker…". Bấm: game mở bằng Chạy nhẹ, vào màn tiêu đề.
  2. Để biết Proton 9 có chạy được game không: trong Chạy nhẹ, menu ☰ → "Chạy bằng Windows". Hộp hiện lại, tắt game;
     ở "Tự sửa lỗi" bấm "Chạy bằng Wine cũ (Proton 9)". Ghi lại kết quả, gửi nhật ký Wine.
  3. Hồi quy: game Windows 32-bit đã chạy được (KiriKiri, Unity x86, game DirectX) vẫn chạy, không có hộp hỏi.

## Thư mục có dấu cách hoặc dấu chấm ở cuối tên ("Game ")

Nhật ký ngày 14/10/2026 (Lifeguard Holic, Unity 6, Xiaomi 23049RAD8C, bản 0.1.42): game đứng ở màn hình chờ, cả 4 lần
mở. Game vẫn vẽ 29 FPS, luồng bận nhất 2%, GPU 7%. `Player.log` của Unity ghi ngay lúc khởi động:

```
System.Exception: Invalid path in TextDataProvider : 'F:/Game /LifeguardHolic-GAMEHUB-AGVN/Lifeguard Holic_Data/StreamingAssets/aa/settings.json'
```

- **Vì sao:** thư mục chứa game tên là `Game ` (dấu cách ở cuối). Bàn phím điện thoại tự thêm dấu cách sau một từ gợi ý,
  và Android giữ nguyên tên đó. Windows không có tên như vậy: Mono và .NET (phần C# của game Unity) cắt dấu cách ở cuối
  mọi thư mục trong đường dẫn, nên game đi tìm `F:/Game/…` và không thấy. Phần C++ của Unity vẫn mở được game, nên
  game lên tới màn hình chờ rồi đứng ở đó: Addressables không nạp được, Naninovel không khởi động được. Wine cũng tự bỏ
  dấu chấm ở cuối tên thư mục, nên thư mục `Game.` thì game không mở được.
- **Từ bản 0.1.43 (`AgvnPathLink`):** khi một thư mục trên đường tới file chạy có tên kết thúc bằng dấu cách hoặc dấu
  chấm, app mở game qua một liên kết trong ổ C:, `C:\agvn-links\Game-<mã>`. Liên kết mang tên đã bỏ dấu cách và trỏ về
  đúng thư mục đó, nên file, save và cài đặt của game vẫn ở chỗ cũ. Chỉ lệnh mở game của lần đó đổi; lối tắt giữ đường
  dẫn thật. `su-kien.txt` ghi một dòng "Thư mục "Game " có dấu cách hoặc dấu chấm ở cuối tên…". Game ở thư mục bình
  thường mở như trước.
- **Trước khi có bản đó:** đổi tên thư mục bỏ dấu cách ở cuối (`Game ` → `Game`), rồi thêm lại game. Save của game Unity
  nằm trong ổ C: của app nên vẫn còn.
- **Test:** `AgvnPathLinkTest`. Test tạo liên kết thật chỉ chạy trên ổ kiểu Linux; trên Windows, nơi anh Sơn dựng bản phát
  hành, test đó được bỏ qua, vì Windows tự cắt dấu cách và cần quyền mới tạo được liên kết.
- **Thử máy:**
  1. Đổi tên thư mục chứa một game Unity thành `Game ` (có dấu cách ở cuối), mở game: game qua màn hình chờ, vào menu.
     `su-kien.txt` có dòng "Thư mục "Game " có dấu cách…".
  2. Lưu, thoát, mở lại: save còn.
  3. Hồi quy: game ở thư mục bình thường không có dòng đó, mở như trước.

## Texture nén BCn với DXVK 2.x (bản 0.1.45)

Nhật ký ngày 15/10/2026 (Av Director Life, Unreal 5, bản GameHub; vivo V2405A: Dimensity 9400, Mali-G925; bản 0.1.44):
game không vào được, 7 lần mở. Ở các lần cuối, game hiện hộp:

```
LowLevelFatalError [File:D:\build\++UE5\Sync\Engine\Source\Runtime\Windows\D3D11RHI\Private\D3D11Util.cpp] [Line: 236]
Direct3DDevice->CreateShaderResourceView(TextureResource,&SRVDesc,ShaderResourceView.GetInitReference()) failed with error E_INVALIDARG
```

và log Wine có:

```
err:   D3D11: Cannot create shader resource view:
err:     Resource type:   3
err:     Resource usage:  8
err:     Resource format: 70
err:     View format:     72
err:     View plane:      0
```

- **Vì sao:** DXGI 70 → 72 là BC1 → BC1_UNORM_SRGB. Nhật ký còn có 74 → 74 (BC2) và 76 → 78 (BC3 → BC3_UNORM_SRGB). GPU
  Mali không đọc được texture BCn, nên app giải nén chúng: wrapper (`libvulkan_wrapper.so`) và lớp `libbcn_layer.so`
  (`bcnEmulation=auto`, `bcnEmulationType=compute`, mặc định).
  - DXVK 2.3.1 cho mỗi texture BC1–BC3 hai dạng xem (UNORM và sRGB), nên ảnh là "mutable". Khi tạo chỗ đọc (shader
    resource view), DXVK hỏi driver dạng đó có đọc được trong shader không, qua `vkGetPhysicalDeviceFormatProperties2`
    (`VkFormatProperties3`; `D3D11CommonTexture::CheckViewCompatibility`). Với BC2 và BC1/BC3 sRGB, câu trả lời là
    không, nên DXVK trả `E_INVALIDARG` và Unreal dừng.
  - Lớp `libbcn_layer.so` chỉ trả lời hàm cũ `vkGetPhysicalDeviceFormatProperties` (các hàm có trong file `.so` không có
    bản `…2`). DXVK 1.10.3 hỏi bằng hàm cũ đó (`DxvkAdapter::formatProperties`), nên không gặp lỗi này: hai lần chạy
    đầu bằng 1.10.3, game chạy gần một phút và 1 phút 40 giây mà không hiện hộp.
  - Chưa rõ phần nào trả "không" cho DXVK 2.x: app không có mã nguồn của đúng bản wrapper và lớp đó. Wrapper đi kèm là
    bản của Pipetto; tên biến môi trường của nó khác bản trên GitHub của leegao.
- **Vì sao game kẹt ở DXVK 2.3.1:** người chơi báo "Màn hình đen dù game vẫn chạy". "Tự sửa lỗi" thử "Đổi Đồ họa sang
  Trung bình" (không giúp), rồi "Đổi DXVK sang bản 2.3.1". Từ đó:
  1. Game tắt ở giây 30, trước khi thanh kịp hỏi (45 giây), nên cách này ở lại trên game.
  2. Hộp của Unreal không phải hộp thông báo của Windows (MessageBox) nên Wine không ghi lại. Wine thoát với mã 0, app
     coi là "kết thúc bình thường" và không hỏi gì. Từ bản 0.1.46 app đọc tiêu đề hộp đó ("Game Unreal báo lỗi rồi tự
     tắt" ở bảng trên).
  3. Thanh màn hình đen chuyển game sang Proton 9 nhưng giữ DXVK 2.3.1 đang thử.
- **Từ bản 0.1.45:**
  - Lỗi mới `dxvk-view`: dòng `D3D11: Cannot create shader resource view` của DXVK, khi game tự tắt. Cách sửa: "Đổi
    DXVK sang bản 1.10.3" (fix `dxvk-old`: chỉ hiện khi game đang dùng DXVK 2.x, đưa về 1.10.3 cùng loại, arm64ec hay
    không; khác `dxvk-other`, nên vẫn hiện dù "Đổi DXVK" đã thử), đổi driver (máy Adreno: Turnip đọc được BCn), gửi nhật
    ký.
  - Cách đang thử mà game tự tắt trước một phút: trả cấu hình về như trước (xem "Người chơi báo lỗi").
  - Thanh màn hình đen trả cách đang thử về trước khi chuyển game sang Proton 9.
  - Game mới thêm vào Proton 9 (xem "Hai bản Wine").
- **Lần mở thứ 3 (DXVK 1.10.3, Proton 10):** hộp "Assertion failed! … vkCreateShaderModule". Trên Mali, "Tự sửa lỗi" có
  sẵn hai cách: tắt bước sửa shader "hằng số" (`WRAPPER_NO_PATCH_OPCONSTCOMP=1`), rồi bước "cắt hình". Hai cách này chưa
  được thử, vì app đang thử cách cho màn hình đen.
- **Chưa làm:** cho lớp giải nén BCn trả lời cả `vkGetPhysicalDeviceFormatProperties2`. Cần mã nguồn của lớp đó; file
  `.so` trong app là bản dựng sẵn.
- **Test:** `AgvnDoctorTest.dxvkCannotReadACompressedTexture`, `AgvnRepairTest.aFixTheGameClosedWithTooSoonGoesBackTried`.
- **Thử máy (máy Mali):**
  1. Av Director Life đang ở DXVK 2.3.1: mở game, hộp của Unreal hiện, bấm OK. Về thư viện, "Tự sửa lỗi" hỏi "Game tắt
     vì DXVK không đọc được texture nén", nút đầu "Đổi DXVK sang bản 1.10.3". Bấm: game mở lại và không còn hộp đó.
     Lần đầu có thể đen 1–2 phút, vì Unreal dịch shader.
  2. Nếu hiện hộp "Assertion failed … vkCreateShaderModule": "Tự sửa lỗi" đề nghị tắt bước sửa shader "hằng số". Bấm,
     game mở lại.
  3. Vòng thử: báo "Màn hình đen dù game vẫn chạy" cho một game, đến khi app thử một cách làm game tự tắt sớm (ví dụ
     DXVK 2.3.1 ở game trên). Game tắt; về thư viện thấy lại lỗi đã báo, dòng đầu "Với cách đang thử (…), game tự tắt
     sau … giây…", cấu hình đã về như trước, có nút thử cách tiếp theo. `su-kien.txt` có "Tự sửa lỗi: game tự tắt sau …
     giây khi đang thử …".
  4. Thêm một game mới: game vào container Proton 9, không có hộp "Đang chuẩn bị Wine mới". Game có phim WMV không chạy
     thì thanh vẫn mời "Chạy lại bằng Wine mới".
  5. Hồi quy: game đã thêm trên Proton 10 vẫn ở Proton 10 và chạy như trước.

## Game chết khi quay lại app (bản 0.1.46)

Nhật ký ngày 16/10/2026 (Rj01608199, game 32-bit dùng Direct2D và Direct3D 11; vivo V2403A: Snapdragon 8 Gen 3, Adreno
750; bản 0.1.44 và 0.1.45): người chơi báo "đen màn hình, đơ máy". Cả 3 lần người chơi rời app rồi quay lại sau 28–59
giây, game tắt ngay khi app cho game chạy tiếp. Lần rời app 3 giây thì game chạy tiếp bình thường.

```
I AGVN    : game resumed after 30 s
I System.out: X connection to :0 broken (explicit kill or server shutdown).    (×5, mọi tiến trình Wine cùng lúc)
```

- **Vì sao:** X server của app chạy một vòng `epoll_wait` cho mọi kết nối (`XConnectorEpoll`, `xconnector_epoll.c`).
  `doEpollIndefinitely` trả `numFds >= 0`, nên khi `epoll_wait` bị ngắt (`EINTR`) vòng đó dừng và `shutdown()` đóng mọi
  kết nối. Wine thấy kết nối X đứt và thoát với mã 1. Trên Linux, `epoll_wait` trả `EINTR` sau khi tiến trình bị dừng
  rồi chạy lại (`man 7 signal`), và khi Android hay hệ điều hành của hãng đóng băng app ở nền rồi rã băng. Vì vậy chỉ lần
  rời app lâu mới làm game chết. Lỗi này có từ bản Winlator gốc. Luồng đọc từng client của ALSA (`waitForSocketRead`,
  `poll`) cũng dừng khi gặp `EINTR`.
- **Từ bản 0.1.46:** cả hai vòng chờ lại khi gặp `EINTR`. Mảng `events` của `epoll_wait` trước đây dùng chung cho ba
  luồng (X server, ALSA, SysV shm) chạy cùng lúc; giờ mỗi lần gọi có mảng riêng.
- **"Tự sửa lỗi" ở bản 0.1.45:** game tắt khi quay lại app bị tính là "tự tắt trước một phút" khi đang thử một cách, nên
  "Đổi DXVK sang bản 1.10.3" và "Đổi driver sang System" bị tính là không giúp. Từ bản 0.1.46, phiên có lúc bị dừng không
  tính như vậy (xem "Người chơi báo lỗi").
- **Chưa rõ:** phần "đen màn hình, đơ" trong lúc chơi. Ở lần 2 (30 phút), trước khi người chơi rời app, game không đổi
  hình 13 giây dù người chơi bấm 20 lần. Nhật ký Wine tắt, nên không biết game kẹt ở đâu. Cần nhật ký có "Bật debug
  Wine" và file `Game_d3d11.log`, `Game_dxgi.log` mà DXVK 1.10.3 ghi trong thư mục game.
- **Log của DXVK 1.x (bản 0.1.46):** DXVK 1.10.3, bản mặc định trên Mali, ghi log qua `std::cerr`, không tới log của
  Wine, nên nhật ký của game chạy bản đó không có dòng DXVK nào. Nó còn ghi mỗi DLL một file cạnh exe (`<exe>_dxgi.log`,
  `_d3d11.log`, `_d3d10.log`, `_d3d9.log`). Từ bản 0.1.46, các file đó được chép vào phiên chơi như log của engine
  (`AgvnEngineLogs`), nên "Gửi nhật ký" có chúng, và "Tự sửa lỗi" đọc phần cuối của chúng như log Wine (các lỗi DXVK
  trong bảng trên giờ nhận ra cả với DXVK 1.10.3). Test: `AgvnSessionLogTest.dxvkOneLogsSitNextToTheExe`.
- **Test:** `AgvnRepairTest.aSessionTheAppStoppedIsKnown`, `AgvnRepairTest.aFixTheGameClosedWithTooSoonGoesBackTried`.
  Phần C không có unit test: thử trên máy.
- **Thử máy:**
  1. Mở một game Windows, chơi một lúc, về màn hình chính, chờ 1–2 phút (tắt màn hình càng tốt), quay lại: game chạy tiếp,
     không tắt. `logcat -s AGVN System.out` không có "X connection to :0 broken".
  2. Làm lại với một game có tiếng: tiếng chạy lại khi quay lại (ALSA).
  3. Đang "Tự sửa lỗi" thử một cách, rời app một lúc rồi quay lại: cách đó không bị tính là "tự tắt" chỉ vì đã rời app.

## Game HTML không có chữ, nhật ký không có gì (bản 0.1.47)

Nhật ký ngày 16/10/2026 (Vệ Tinh Đức Miễn Phí v2.0.1, bản RPG Maker dạng web "JoiPlay universal", chạy bằng "Chạy nhẹ";
HONOR AAK-AN00: Snapdragon 8 Elite, Adreno 830, Android 17; bản 0.1.46): game vào được mà không có chữ. Gói nhật ký chỉ
có `app/chay-nhe.txt` (`runner=html`, `started=1`: trang đã mở, không lỗi, không ảnh nào để trống), `app/thiet-bi.txt`
và `app/logcat.txt`. Logcat là một dòng `--------- beginning of (HKS)…`: máy HONOR mã hoá logcat của app.

- **Vì sao không biết gì:** mọi thứ trang game báo (lỗi script, câu `[AGVN] skipped game error: …` của
  `html-compat.js`, phông hay file data không tải được) chỉ ghi vào logcat (`AgvnHtmlConsole`, tag `AgvnHtml`), và file
  game thiếu cũng vậy (`HTML game file missing`). Trên máy HONOR các dòng đó không đọc được. Gói gửi lúc 14:48 cùng ngày
  (bản 0.1.38) không có cả `chay-nhe.txt`: file này chỉ có khi game đang mở, vì thư viện đọc xong lần chơi đã kết thúc
  thì xoá nó.
- **Từ bản 0.1.47, `chay-nhe.txt` của game HTML có thêm** (`AgvnLightSession.keeper`):
  - `page`: 8 cảnh báo và lỗi đầu tiên của trang, mỗi câu một lần, kèm file và dòng trong thư mục game
    (`AgvnHtmlConsole.brief`: `… (/js/plugins/Tên plugin.js:12)`);
  - `missing`: 8 file đầu tiên trang xin mà thư mục game không có (phông, file data, tiếng, phim). Câu hỏi `HEAD` của
    `fs.existsSync` (`html-compat.js`) chỉ hỏi file có hay không, nên không tính;
  - `standIn`: ảnh `.png` thiếu (app đưa ảnh trong suốt) giờ cũng được ghi, như ảnh mã hoá (`… (missing)`).
  Mỗi danh sách cũng được nhớ trong bộ nhớ, nên một tiếng game xin lại mỗi lần phát, hay một tràng thông báo, không đọc
  và ghi file mỗi lần.
- **Lần chơi trước:** thư viện đọc xong lần chơi đã kết thúc thì giữ file thành `files/agvn/light-session-last.properties`
  (thay cho lần trước nữa). "Gửi nhật ký" của đúng game đó có thêm `app/chay-nhe-lan-truoc.txt`, nên gửi từ thư viện sau
  khi thoát game vẫn có.
- **`thiet-bi.txt`** có thêm dòng `WebView (game HTML): <phiên bản> · <gói>`: game HTML chạy trong WebView của máy, và máy
  bán ở Trung Quốc (như HONOR AAK-AN00) thường có WebView riêng của hãng, không phải bản của Google.
- **Chưa rõ:** vì sao Vệ Tinh Đức không có chữ. Cần gói nhật ký từ bản 0.1.47 (gửi lúc game đang mở, hay ngay sau khi
  thoát), có `page`, `missing` và dòng WebView.
- **Test:** `AgvnLightNotesTest`.
- **Thử máy:**
  1. Game RPG Maker MV/MZ "Chạy nhẹ": đổi tên một file trong `audio/se/` (tiếng con trỏ, như `Cursor1.ogg`), thêm dòng
     `console.warn('AGVN thu');` vào đầu một file trong `js/plugins/`, mở game, di con trỏ vài lần rồi bấm "Gửi nhật ký"
     trong menu Chạy nhẹ: `app/chay-nhe.txt` có `missing=` tên file tiếng đó một lần (dù tiếng phát nhiều lần) và
     `page=` có `AGVN thu (/js/plugins/<file>.js:1)`. `app/thiet-bi.txt` có dòng `WebView (game HTML): …`.
  2. Thoát game, về thư viện, bấm "Gửi nhật ký" của game đó trong menu ⋮: có `app/chay-nhe-lan-truoc.txt` với các dòng
     trên. "Gửi nhật ký" của một game khác thì không có file này.
  3. Game chạy như trước: tiếng thiếu vẫn bị bỏ qua, ảnh thiếu vẫn để trống, không có hộp lỗi mới.

## App tự tắt khi mở game: tên quá dài cho WinHandler (bản 0.1.48)

Nhật ký ngày 16/10/2026 (OPPO PLE110: Snapdragon, Adreno 825, Android 16; bản 0.1.46). Người chơi gửi gói của hộp
"Ứng dụng vừa gặp lỗi. Xuất nhật ký để gửi AGVN?" (`AGVN-nhat-ky-<ngày>-<giờ>.zip`) khi hỏi về Song Tu Vo Lam đen màn
hình. Gói có logcat với 4 lần app tắt hẳn ngày 15/10, 16:23–16:27, mỗi lần một tiến trình mới:

```
E AndroidRuntime: FATAL EXCEPTION: pool-20-thread-1
E AndroidRuntime: java.nio.BufferOverflowException
E AndroidRuntime:   at java.nio.ByteBuffer.put(ByteBuffer.java:1329)
E AndroidRuntime:   at com.winlator.cmod.winhandler.WinHandler.lambda$setProcessAffinity$3(WinHandler.java:209)
```

- **Vì sao:** khi một cửa sổ game hiện ra, app gán nhân CPU cho tiến trình của nó (`assignTaskAffinity`). Cửa sổ không có
  `_NET_WM_PID` thì app gửi tên lớp cửa sổ (`WM_CLASS`, tức tên file exe) cho `agvn-winhandler.exe`. Gói tin chỉ có 64
  byte (`winhandler.c` cũng chỉ đọc 64), tên chiếm 14 byte sau phần đầu, nên tên dài hơn 50 byte làm `ByteBuffer.put`
  ném lỗi trong luồng gửi, và lỗi đó tắt cả app. `WM_CLASS` được đọc như Latin-1 rồi ghi lại bằng UTF-8, nên mỗi byte
  của chữ Trung, Nhật thành 2 byte: tên exe 8 chữ Hán cộng `.exe` đã là 52 byte. `exec` và `killProcess` của Trình quản
  lý tác vụ cũng có lỗi này với lệnh hay tên dài. Lỗi có từ bản Winlator gốc.
- **Từ bản 0.1.48:** yêu cầu nào không vừa gói tin thì không gửi; `logcat -s AGVN` có `WinHandler request not sent`, và
  `su-kien.txt` có `Yêu cầu tới Windows không gửi được (quá dài): …`. Phía Windows chỉ đọc 64 byte và so cả tên, nên với
  tên dài như vậy việc gán nhân CPU vốn không có tác dụng: bỏ yêu cầu không đổi gì cho game.
- **Gói của hộp báo lỗi** (`DiagnosticsExporter`) trước đây chỉ có `thiet-bi.txt`, logcat, `last-crash.txt` và hai file
  log rời, không có lần chơi nào, dù app tắt lúc đang chơi. Từ bản 0.1.48 gói có thêm 3 lần chơi mới nhất của mọi game
  (`phien/<game>/<lần chơi>/`, xếp theo giờ trong tên thư mục), mỗi file tối đa 3 MB như các file khác.
- **Song Tu Vo Lam hiện hai mục ở "Thêm game":** thư mục có hai file exe (`SongTu_GameHub.exe` và `雙修武林.exe`), app
  không nhận ra engine, nên coi là một bộ nhiều game (`GameExeResolver.gameExes`) và hiện mỗi exe một
  mục (`Song Tu Vo Lam - SongTu_GameHub`, `Song Tu Vo Lam - 雙修武林`). App không biết exe nào là game, exe nào là bản
  chép hay trình mở. Cả ba lối tắt của game trên máy (hai container) có `agvn_last_start_ms=0`: chưa lần nào hiện hình.
  Lý do màn hình đen cần nhật ký "Gửi nhật ký" của chính game đó.
- **Test:** `DiagnosticsExporterTest`. Phần WinHandler không có unit test (cần Activity): thử trên máy.
- **Thử máy:**
  1. Mở một game Windows tên ngắn: game chạy như trước; `logcat -s AGVN` không có `WinHandler request not sent`.
  2. Trong game, "Quản lý tiến trình" → "Tác vụ mới", gõ một lệnh dài hơn 60 ký tự: app không tắt; `logcat -s AGVN` có
     `WinHandler request not sent`.
  3. Làm app gặp lỗi (hay chờ lần lỗi thật), mở lại app, bấm "Xuất": gói trong Download có thư mục `phien/` với 3 lần
     chơi mới nhất.

## Chạm làm app tắt: số ngón của touchpad bị lệch (bản 0.1.49)

Báo cáo ngày 16/10/2026 (POCO F8 Pro, Android 16, HyperOS 3; bản 0.1.42; một game Unity bản PC chạy Windows, đang ở màn
nạp lâu). Người chơi chạm liên tiếp khoảng 15 lần trong 2 giây rồi vuốt hai lần. Ở lần vuốt thứ hai app tắt hẳn, game mất
theo, không có hộp báo lỗi. Trước đó game vẫn chạy (153% CPU).

```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: java.lang.NullPointerException: Attempt to read from field 'long com.winlator.cmod.widget.TouchpadView$Finger.touchTime' on a null object reference
E AndroidRuntime:   at com.winlator.cmod.widget.TouchpadView.handleTouchpadEvent(TouchpadView.java:319)
E AndroidRuntime:   at com.winlator.cmod.widget.TouchpadView.onTouchEvent(TouchpadView.java:199)
E AndroidRuntime:   at com.winlator.cmod.widget.InputControlsView.onTouchEvent(InputControlsView.java:744)
```

- **Vì sao:** `TouchpadView` giữ các ngón trong `fingers[]` nhưng đếm riêng bằng `numFingers`: cộng một mỗi lần đặt ngón,
  trừ một khi nhấc một ngón đang có. Chạm tới touchpad qua `InputControlsView` (ngón đặt trên nút điều khiển ảo ở lại nút
  đó) và qua `XServerDisplayActivity.dispatchTouchEvent` (bỏ mọi chạm khi game đang tạm dừng ⏸), nên có lúc ngón nhấc lên
  mà touchpad không biết. Ngón đó nằm lại trong mảng; lần đặt ngón sau ghi đè lên nó mà vẫn cộng thêm, nên số đếm trôi
  dần lên. Ở chế độ chạm giả lập màn cảm ứng, khi ngón số 1 đặt xuống lúc số đếm từ 2 trở lên, mã đọc thời điểm chạm của
  ngón số 0. Ngón 0 không có ở touchpad (đang trên nút ảo, hoặc đã bị ghi đè rồi nhấc), nên lỗi NullPointerException trên
  luồng chính tắt cả app. Số đếm lệch còn làm một lần chạm bị tính là hai ngón: nút trái đã nhấn không được nhả (kẹt nút
  trái). Lỗi có từ bản Winlator gốc.
- **Từ bản 0.1.49 (`TouchpadView`):**
  - lúc đặt ngón, ngón nào còn trong mảng mà không còn trên màn hình (không có trong sự kiện) thì bị bỏ, và nút chuột nó
    đang giữ được nhả (`dropFinger`);
  - số ngón được đếm lại từ mảng sau mỗi thay đổi (`countFingers`), nên không lệch được nữa;
  - ngón số 1 so thời điểm với một ngón khác đang có trên touchpad, không đọc thẳng ngón 0;
  - khi Android lấy lại cử chỉ (`ACTION_CANCEL`, như vuốt từ mép màn hình), nút chuột các ngón đang giữ được nhả;
  - lỗi khác lúc xử lý chạm thì ghi `touch not handled` vào `logcat -s AGVN` và một dòng `Lỗi xử lý chạm, đã bỏ qua: …`
    vào `su-kien.txt`, rồi bỏ các ngón: một lần chạm không bao giờ tắt app nữa.
- **Test:** không có unit test (cần View và X server). Thử trên máy.
- **Thử máy** (chế độ touchpad, có nút điều khiển ảo):
  1. chạm dồn dập 20 lần trong 2 giây rồi vuốt hai ngón;
  2. giữ một ngón trên nút ảo, ngón kia chạm và vuốt vùng game;
  3. đang chạm thì vuốt từ mép màn hình để gọi cử chỉ hệ thống, rồi quay lại game.

  Cả ba: app không tắt, con trỏ trong game ở đúng chỗ, không kẹt nút trái. Chạm một ngón vẫn là nhấn trái, chạm hai ngón
  vẫn là nhấn phải như trước.

## Game chỉ vẽ bằng OpenGL, và nhật ký riêng cạnh file chạy (bản 0.1.50)

Nhật ký The Agnietta (cocos2d-x, JavaScript bằng SpiderMonkey `mozjs-33.dll`, 32-bit, Xiaomi 2510DRA23E, Mali-G57 MC2,
17/10/2026): game không vẽ khung nào hơn một phút, một nhân CPU bận 90–99%. Lần duy nhất game vẽ được (19 khung/giây ở
giây 79), game tự thoát với mã 0, không có hộp lỗi. "Tự sửa lỗi" cho thử "Đổi DXVK sang bản 2.3.1", dù game vẽ bằng
OpenGL (`glew32`, `opengl32`, qua Zink) và DXVK không chạy. "Gửi nhật ký" cũng không có `action_log.txt` và `thread.log`,
hai nhật ký của chính game nằm cạnh file chạy.

- **Game chỉ vẽ bằng OpenGL (`AgvnGlGame`):** file chạy, hoặc một DLL cạnh nó, nạp `opengl32`, và không file nào nạp
  Direct3D, DXGI hay DirectDraw (`d3d*`, `dxgi`, `ddraw`; `d3dcompiler_47` của ANGLE cũng tính là Direct3D). Chỉ xét game
  có engine app chưa biết: Unity, Unreal và các engine app đã biết tự nạp Direct3D lúc chạy, nên danh sách nạp không cho
  thấy. Game chưa ghi engine thì app nhận diện engine trước. Kết quả được nhớ theo file chạy trong lúc app mở.
  - Với game như vậy, `AgvnFixes.dxvk` trả về sai, nên các cách sửa chỉ đổi DXVK không được đề nghị: đổi bản DXVK,
    DXVK arm64ec, WineD3D, tắt async, giải nén mọi texture BCn, "Bật Đồng bộ khung hình".
  - Driver, chế độ dựng hình của Turnip, các bước sửa shader cho Mali, giả lập CPU và Wine cũ vẫn được đề nghị, vì
    OpenGL qua Zink cũng đi qua chúng.
- **Nhật ký riêng cạnh file chạy (`AgvnGameDirLogs`):** ngoài các tên nhật ký app đã biết (Unity, Unreal, Ren'Py,
  DxLib, BepInEx, DXVK 1.x, cheat AGVN), app chép thêm các file `*.log` và `*log*.txt` nằm ngay cạnh file chạy (không
  lấy `changelog.txt`), tối đa 8 file mới nhất. Như mọi nhật ký khác, file chỉ được chép khi được ghi trong lần chơi
  đó, và file lớn được cắt bớt phần giữa.
- **Test:** `AgvnGlGameTest`, `AgvnSessionLogTest.aGamesOwnLogsBesideItsExeComeToo`.
- **Thử máy:**
  1. The Agnietta: "Tự sửa lỗi" không còn "Đổi DXVK…"; "Gửi nhật ký" có `action_log.txt` và `thread.log` của lần chơi.
  2. Hồi quy: một game Unity và một game DirectX 9/11 vẫn có "Đổi DXVK…" trong "Tự sửa lỗi".

## Game có bản 32-bit và 64-bit: chạy bản 64-bit (bản 0.1.51)

Nhật ký Being Adik 0.12.1 (Ren'Py 7.4.10, realme RMX3686, Mali-G68 MC4, 17/10/2026): cửa sổ game đen, `log.txt` của
Ren'Py ghi mọi cách vẽ đều hỏng (gl2, angle2, gles2, gl, angle, gles, sw), lỗi cuối là "No matching GL pixel format
available". Game được thêm bằng `BeingADIK-32.exe`, vì tên này đứng trước `BeingADIK.exe` khi xếp theo tên (`-` đứng
trước `.`). Hai lý do cộng lại:

- OpenGL: Zink không khởi động được trên driver Vulkan 1.1 của Mali-G68 (`failed to load driver: zink`, thiếu
  `logicOp`), nên `gl2`/`gl` hỏng. App không có VirGL hay llvmpipe để thay.
- Direct3D: Ren'Py chuyển sang ANGLE (OpenGL ES trên Direct3D 11, qua DXVK). Nhưng chương trình 32-bit chạy qua wow64,
  và driver Mali không đặt được bộ nhớ đồ họa vào vùng 4 GB mà chương trình 32-bit với tới.
  `BeingADIK-32_d3d11.log` ghi 36 dòng `Mapping memory failed with VK_ERROR_MEMORY_MAP_FAILED`, rồi `Memory allocation
  failed`, rồi `Failed to create D3D11 device`. Nên `angle2`/`gles2` cũng hỏng.

Bản 64-bit `BeingADIK.exe` không bị giới hạn bộ nhớ này: ANGLE có DirectX 11 qua DXVK như các game 64-bit khác trên máy.

- **Thêm game (`GameExeResolver`, `AgvnExe64.twin`):** khi cạnh "`<Tên>`-32.exe" có "`<Tên>`.exe" là file 64-bit, app bỏ
  bản -32. Với game Ren'Py, bản 64-bit còn cần thư mục `lib/…windows-x86_64` (`lib/windows-x86_64`,
  `lib/py2-windows-x86_64`, `lib/py3-windows-x86_64`). Nếu `lib` chỉ có `windows-i686`, app giữ bản -32. Bộ công cụ PC
  (`agvn_profile_lib.resolve_exe`) làm giống vậy. Game đã thêm từ trước không đổi gì.
- **Cách sửa "Chạy bản 64-bit của game" (`exe-64`):** chỉ có cho game đang chạy "`<Tên>`-32.exe" mà có bản 64-bit như
  trên. Cách sửa này bật `agvnExe64` của game. Lúc mở game, `AgvnExe64.apply` đổi file chạy trong lệnh mở, giống
  `AgvnExeRedirect`; đường dẫn của game giữ nguyên. Nhật ký phiên ghi một dòng: "Chạy bản 64-bit của game: … thay cho
  …". "Tự sửa lỗi" trả lại `agvnExe64` như các cài đặt khác khi cách sửa không giúp được. Thứ tự trong danh sách cách sửa:
  - đứng đầu: lỗi mới `dxvk-map-32`, "Game báo thiếu DirectX", "Màn hình đen", "Game tự tắt, văng ra";
  - sau "Chạy nhẹ" (RPG Maker): game tắt ngay khi mở, game bị lỗi và tự tắt;
  - sau giả lập ổn định và mod: game đứng hình;
  - sau hạ Đồ họa: game hết RAM. Chương trình 32-bit chỉ có 4 GB bộ nhớ.
  - sau "Trả về cấu hình chạy tốt": lỗi khác.
- **Lỗi `dxvk-map-32`:** `"when": []`, nên app tìm cả khi game không tự tắt (Ren'Py để cửa sổ đen tới khi người chơi
  thoát). Trước bản này, app không thấy lỗi gì ở những lần chạy như vậy, nên chúng có thể được ghi là "chạy tốt". Khi DXVK không đặt được bộ nhớ đồ họa vào
  game, nó không tạo được thiết bị DirectX, nên dòng lỗi này không xuất hiện ở game chạy được.
- **Test:** `AgvnExe64Test`, `AgvnDoctorTest.aThirtyTwoBitGameGetsNoGpuMemory`,
  `test_toolkit.test_renpy_runs_its_64bit_exe`.
- **Thử máy:**
  1. Being Adik trên máy Mali: bấm "Tự sửa lỗi" → "Màn hình đen dù game vẫn chạy". Cách đầu tiên phải là "Chạy bản 64-bit
     của game (BeingADIK.exe)". Sau khi chọn, nhật ký có dòng "Chạy bản 64-bit của game", và `BeingADIK_d3d11.log`
     không có `VK_ERROR_MEMORY_MAP_FAILED`. Game hiện menu, nếu DXVK 64-bit chạy được trên máy đó.
  2. Thêm lại game Ren'Py 7 có cả hai file chạy: game được thêm bằng `<Tên>.exe`.
  3. Hồi quy: một game Ren'Py đang chạy tốt bằng bản -32 vẫn mở như cũ cho tới khi người chơi chọn cách sửa. Một game
     chỉ có một file chạy không có cách sửa này.

## Wine lỗi khi game gọi dịch vụ của Windows (bản 0.1.52)

Nhật ký Three Gals (Unreal Engine 5, bản đóng gói GameHub, Xiaomi 2510DPC44G, Snapdragon 8 Elite / Adreno 830,
app 0.1.42, 18/10/2026): ba lần chơi đều qua logo, rồi đúng một lỗi truy cập bộ nhớ trên luồng game, ngay sau khi
âm thanh của game khởi động. Unreal tự bắt lỗi đó (luồng `CrashReportingThread` gom thông tin máy), nên Wine không in
lỗi nào của nó, và game đứng màn hình đen tới khi tắt. Máy không hết RAM. Bản shipping của Unreal không ghi log
riêng, nên nhật ký Wine là tất cả những gì app có.

- Backtrace của lỗi: `rpcrt4.dll` (phần gọi dịch vụ, RPC) ← `sechost.dll` (`OpenSCManagerW`, mở trình quản lý dịch
  vụ của Windows) ← `ThreeGals-Win64-Shipping.exe`. Lỗi nằm trong Wine chứ không phải trong game.
- Game chạy trên Proton 10 (bản arm64ec): app 0.1.42 thêm game mới vào Proton 10. Từ 0.1.45 game mới vào Proton 9. Ở
  Proton 9, `NdrClientCall2` (phần đóng gói lời gọi RPC) là mã x64 chạy qua bộ giả lập; ở Proton 10 nó là mã ARM64EC,
  nên lời gọi đi theo đường khác.
- **Lỗi mới `wine-rpc-crash`:** dòng backtrace có `L"rpcrt4.dll"`. `"when": []`, nên app tìm cả khi game đứng màn hình
  đen rồi bị vuốt tắt (đọc phần cuối nhật ký Wine đã giữ lại, `AgvnDoctor.afterKill`). Thứ tự cách sửa: Chạy bằng Wine
  cũ (Proton 9, chỉ hiện cho game đang chạy Proton 10), giả lập CPU ổn định, gửi nhật ký. Trong nhật ký Three Gals, mỗi lần chơi có đúng một backtrace,
  chính là lỗi này; nhật ký Being Adik (Ren'Py) không có backtrace nào.
- **Test:** `AgvnDoctorTest.wineFailsInsideItsRpc`.
- **Thử máy:**
  1. Three Gals đang chạy Proton 10: mở game, chờ màn hình đen, thoát hoặc vuốt tắt app. Lần mở app sau, app hỏi
     "Wine bị lỗi khi game gọi dịch vụ của Windows", cách đầu tiên là "Chạy bằng Wine cũ (Proton 9)". Chọn xong, game
     qua được cảnh đầu và nhật ký không còn backtrace `rpcrt4.dll`. Nếu vẫn lỗi trên Proton 9, gửi nhật ký mới.
  2. Hồi quy: một game Unreal và một game Unity đang chạy tốt không bị hỏi gì sau khi chơi.

## Game RPG Maker dừng ở màn tiêu đề: tên file viết khác trên điện thoại (bản 0.1.53)

Báo cáo ngày 18/10/2026 (`BAO-CAO-LOI-AGVN-PLAYER-RGSS.md`: một game RPG Maker VX Ace có kèm RTP, Android 16, Mali-G57,
mức máy YEU, bản 0.1.52). Ở "Chạy nhẹ", logo chạy xong, vào màn tiêu đề thì game dừng:

```
Script '<script âm lượng>' line N: Errno::ENOENT occurred.
No such file or directory - Audio/BGM/bgm<6 chữ Nhật>
  ... in `bgm_play': No such file or directory - Audio/BGM/bgm������������������ (Errno::ENOENT)
  from Scene_Title:133:in `play_title_music'
```

Người báo đã kiểm trên PC và trong file zip chép sang máy: file có thật, tên trong dữ liệu game (`System.rvdata2`) khớp
từng byte với tên file, dạng NFC. Trên Windows game chạy bình thường.

- **mkxp-z tìm file thế nào:** lúc mở game, mkxp-z ghi lại tên mọi file trong thư mục game
  (`FileSystem::createPathCache`): chữ A–Z hạ thành chữ thường, mọi byte khác giữ nguyên. Khi game hỏi một file,
  mkxp-z cũng chỉ hạ A–Z ở tên đó rồi so với danh sách theo từng byte (`openRead`). Vậy tên trên máy phải giống từng byte
  tên game hỏi, chỉ trừ hoa thường của A–Z. Tên đi thẳng từ Ruby tới PhysFS, không qua JNI hay bảng mã nào.
- **Đã thử:** dựng lại đúng đoạn tìm file này của mkxp-z với PhysFS 3.2.0 cho Android arm64 (NDK 29, chạy bằng qemu).
  Tên Nhật dạng NFC được tìm thấy. `ENOENT` chỉ xảy ra khi tên trên máy khác byte với tên game hỏi, ví dụ tên lưu dấu
  tách rời khỏi chữ (NFD: `ガ` = `カ` + `゙`). Vậy nhiều khả năng tên file trên điện thoại khác tên trong dữ liệu game,
  do công cụ chép hoặc giải nén. Nhật ký lần này không có tên file trên máy, nên chưa biết chắc.
- **Dòng thứ hai đầy dấu `�`:** mkxp-z tạo câu lỗi dạng byte không mã (`ASCII-8BIT`). Khi ghép câu đó với dòng có tên
  script tiếng Nhật, Ruby thay mỗi byte ngoài ASCII bằng một `�`. Tên game hỏi vẫn đúng: 18 byte là 6 chữ.
- **Tên game hỏi và tên trên máy có thể khác nhau ở:**
  - dấu và dấu kana viết tách (NFD) thay cho viết liền (NFC);
  - hoa thường của chữ ngoài A–Z: Windows bỏ qua, mkxp-z thì không. Ví dụ chữ Latin có dấu, chữ toàn chiều rộng
    `Ａ`/`ａ`, chữ Hy Lạp, chữ Nga;
  - vài dấu tiếng Nhật mà bộ giải mã Shift_JIS và Windows (CP932) viết khác nhau: `〜`/`～`, `‖`/`∥`, `−`/`－`, `¢`/`￠`,
    `£`/`￡`, `¬`/`￢`, `—`/`―`.
- **Từ bản 0.1.53 (`agvn_files.rb`, script AGVN mà mkxp-z nạp trước game):** khi mkxp-z báo không có file ở
  `Audio.bgm_play`, `bgs_play`, `me_play`, `se_play`, `Bitmap.new`, `Graphics.transition` hay `Graphics.play_movie`, app
  tìm trong thư mục game, từng thư mục một, tên giống tên game hỏi sau khi bỏ ba khác biệt trên. Cách so: chuẩn hoá NFD,
  hạ chữ theo Unicode, đưa các dấu Nhật về một kiểu. Tìm thấy thì app hỏi mkxp-z lại bằng tên trên máy. Kết quả được nhớ
  trong lần chơi đó, nên file dùng nhiều (tiếng bấm nút) không phải tìm lại.
  - Chuẩn hoá Unicode cần hai file của Ruby 3.1.3 (`lib/unicode_normalize/normalize.rb`, `tables.rb`; giấy phép Ruby).
    mkxp-z không mang thư viện Ruby nào, nên app chép hai file này vào `files/rgss/ruby`. Thiếu chúng thì app vẫn tìm
    được tên khác hoa thường hay khác dấu Nhật, và game vẫn mở như trước.
  - Game không chậm thêm: chỉ khi mkxp-z không thấy file thì mới có bước tìm này.
  - File không có thật thì game vẫn dừng với lỗi của chính nó. Hộp lỗi vẫn chỉ đúng dòng script của game, không chỉ
    vào `agvn_files.rb`, và câu lỗi giữ nguyên chữ Nhật (UTF-8) thay vì một `�` cho mỗi byte.
- **Nhật ký `tim-tep.txt`:** mỗi tên tìm ra theo cách trên ("Tìm ra: game hỏi …, trên máy là …") và mỗi file không có
  ("Không có: game hỏi …; gần nhất trong <thư mục>: …", kèm 3 tên gần nhất) được ghi một lần. Chữ ngoài ASCII có kèm mã
  Unicode (`<30AB 3099>`), tối đa 40 dòng mỗi lần chơi. Khi game dừng vì lỗi, file nằm trong thư mục phiên
  (`AGVN-Player/logs/<game>/<giờ>/`). "Gửi nhật ký" cũng gửi kèm (`app/tim-tep.txt`) khi đó là game vừa chơi.
- **Lỗi 2 của báo cáo ("Chạy bằng Windows" → hộp "No access to memory location."):** đây là lỗi `ERROR_NOACCESS` khi
  `DllMain` của `RGSS301.dll` gặp lỗi truy cập bộ nhớ, như ca của bản 0.1.42 (xem "DLL ở bộ nhớ chung" ở trên). Ca đó
  chạy Proton 10. Game thêm từ 0.1.45 vào Proton 9, nơi nguyên nhân đó không có. Nhật ký lần này không ghi bản Wine và
  không bật nhật ký Wine, nên chưa biết nguyên nhân.
  - Từ 0.1.53, `tom-tat.txt` của mỗi phiên Windows có dòng `Wine: …` (`proton-9.0-arm64ec` hoặc
    `Proton-10.0-4-arm64ec-9`).
  - Cần thêm: bật "Bật debug Wine", chạy game bằng Windows một lần, rồi "Gửi nhật ký".
- **Test:**
  - `scripts/agvn/mkxp-z/test-agvn-files.rb` (trên PC, Ruby 3.1 trở lên): tìm file như mkxp-z với tên NFD, chữ toàn chiều
    rộng khác hoa thường, dấu `～`, thư mục tên Nhật, file thiếu thật, và khi không có thư viện chuẩn hoá;
  - `AgvnRgssConfigTest.filesLogGoesWithItsOwnGameOnlyAndOnlyWithALine`.
- **Thử máy:**
  1. Game của báo cáo ở "Chạy nhẹ": vào màn tiêu đề và có nhạc. Nếu game vẫn dừng, gửi nhật ký: `tim-tep.txt` cho biết
     tên file trên máy.
  2. Một game VX Ace thử, có `Audio/BGM/bgmガイド.ogg` làm nhạc tiêu đề và `Graphics/Pictures/ゲーム.png`. Trên PC đổi tên
     hai file sang NFD, rồi chép thư mục sang máy và chạy: có nhạc, có ảnh, `tim-tep.txt` có hai dòng "Tìm ra". Lệnh
     PowerShell đổi tên trong thư mục game (file và thư mục sâu nhất trước):
     `Get-ChildItem -Recurse | Sort-Object { $_.FullName.Length } -Descending | % { $n = $_.Name.Normalize([Text.NormalizationForm]::FormD); if ($n -ne $_.Name) { Rename-Item -LiteralPath $_.FullName -NewName $n } }`
  3. Hồi quy: game RPG Maker tên file ASCII vẫn chạy như cũ, FPS không đổi, và "Gửi nhật ký" không có `tim-tep.txt`.
  4. Một game Windows bất kỳ: `tom-tat.txt` của phiên có dòng `Wine: …` đúng với Wine của game.

## Tên file bị giải nén sai bảng mã (bản 0.1.54)

Nhật ký Rj245859 (RPG Maker VX Ace, gói AGVN có cheat, nubia Z2465N, Unisoc UMS9620, Mali-G57, bản 0.1.53,
18/10/2026). Ở "Chạy nhẹ", game vẫn dừng ở màn tiêu đề với `Errno::ENOENT` cho `Audio/BGM/bgmオープニング`. Lần này
`tim-tep.txt` cho thấy tên file trên điện thoại:

```
Không có: game hỏi "Audio/BGM/bgmオープニング" <30AA 30FC 30D7 30CB 30F3 30B0>; gần nhất trong "Audio/BGM":
  "bgm銉曘偅銉笺儷銉�3.ogg" <9289 66D8 5045 9289 7B3A 5137 9289 FFFD>, "bgm1.ogg", "bgm5.ogg"
```

- **Vì sao:** công cụ giải nén trên máy đọc byte UTF-8 của tên tiếng Nhật như thể chúng là mã Trung Quốc (GB18030),
  rồi lưu tên đã đọc sai đó. `bgm銉曘偅銉笺儷銉�3.ogg` chính là `bgmフィールド3.ogg`. Byte cuối của chữ `ド` không đọc
  được thành chữ Trung nào nên thành `�`, và byte đó mất hẳn. Viết lại tên trên máy bằng GB18030 thì ra đúng byte UTF-8
  của tên trong game, chỉ thiếu byte đã mất. Lỗi không nằm ở game hay ở mkxp-z: trên máy, các file của game đã mang tên
  khác.
- **Từ bản 0.1.54 (`agvn_names.rb`, dùng trong `agvn_files.rb`):** khi không có tên nào khớp theo cách của bản 0.1.53,
  app đọc ngược tên trên máy. Nó viết lại tên đó bằng từng bảng mã mà công cụ giải nén có thể đã dùng nhầm (GB18030,
  Shift_JIS/CP932, Big5, CP949, CP437, Windows-1252, ISO-8859-1), rồi so với tên game hỏi viết bằng các bảng mã tên file
  hay dùng (UTF-8, Shift_JIS/CP932, GB18030, Big5, CP949). Mỗi `�` thay cho 1–2 byte ngoài ASCII đã mất.
  - Tên đọc ngược không mất byte nào thì được dùng ngay.
  - Tên có byte mất chỉ được dùng khi trong thư mục chỉ có đúng một tên như vậy khớp (không tính khác phần đuôi). Nếu
    có hai tên cùng khớp, app không đoán, và game báo lỗi như cũ.
  - Tên không phải UTF-8 (công cụ giữ nguyên byte, ví dụ byte Shift_JIS) được so thẳng theo byte.
  - Ruby trong mkxp-z có sẵn mọi bảng mã này (`--with-static-linked-ext`, `libenc.a`, `libtrans.a`). Việc đọc ngược chỉ
    chạy khi mkxp-z không thấy file, mỗi thư mục làm một lần.
- **Nhật ký:** dòng "Tìm ra" có thêm cách tên bị hỏng, ví dụ `tên bị giải nén sai bảng mã: UTF-8 đọc thành GB18030`
  hoặc `Windows-31J giữ nguyên byte`.
- **Cách sạch nhất vẫn là giải nén lại** bằng công cụ cho chọn bảng mã của tên file, và chọn UTF-8. Khi hai tên gốc
  khác nhau chỉ ở byte đã mất, công cụ giải nén sai đã ghi đè file này bằng file kia, và app không lấy lại được file bị
  đè.
- **"Chạy bằng Windows" vẫn hiện "No access to memory location."**, cả ba lần, với `Wine: proton-9.0-arm64ec` (dòng mới
  của 0.1.53). Vậy Proton 9 cũng gặp, dù `ntdll.so` của Proton 9 (gói `proton-9.0-arm64ec`) đọc DLL ở bộ nhớ chung vào
  bộ nhớ thường (`noexec file system, falling back to read`). Nguyên nhân của bản 0.1.42 (Proton 10 ánh xạ DLL không có
  quyền chạy) không áp dụng ở đây, và chưa biết nguyên nhân. Nhật ký mặc định không có kênh `warn` của Wine. Cần bật
  "Bật debug Wine" (mặc định `warn+all,err+all,fixme+all`), chạy game bằng Windows một lần rồi gửi nhật ký. Trong lúc
  chờ, "Tự sửa lỗi" vẫn đề nghị "Chạy nhẹ" trước.
- **Test:** `scripts/agvn/mkxp-z/test-agvn-files.rb`, với đúng các tên trong nhật ký này (một tên mất byte, một tên có
  chữ vùng riêng U+E15C, một tên không mất byte), tên UTF-8 đọc thành Windows-1252, byte Shift_JIS giữ nguyên, và hai
  tên mất byte cùng khớp (không chọn tên nào). Bài thử nạp sẵn các bảng mã trước khi xoá `$LOAD_PATH`, vì Ruby trên PC
  nạp chúng từ thư viện, còn mkxp-z có sẵn.
- **Thử máy:**
  1. Rj245859 trên nubia Z2465N (chưa giải nén lại): "Chạy nhẹ" vào màn tiêu đề, có nhạc. "Gửi nhật ký" có
     `tim-tep.txt` với các dòng "Tìm ra … UTF-8 đọc thành GB18030".
  2. Chơi tiếp vài cảnh có tiếng và ảnh tên tiếng Nhật: không dừng vì `ENOENT`.
  3. Hồi quy: một game RPG Maker tên file đúng vẫn chạy như cũ, không có dòng nào trong `tim-tep.txt`.

## Game 32-bit màn hình đen trên Mali: nhật ký của wrapper (bản 0.1.55)

Nhật ký The Clumsy Witch Seris (WOLF RPG, `GamePro.exe` 32-bit, DirectX 9; vivo V2160, Dimensity 700, Mali-G57 MC2,
driver System Vulkan 1.1; Proton 9; bản 0.1.54; 19/10/2026): năm lần mở, màn hình đen.

- DXVK 1.10.3 (`GamePro_d3d9.log`) tạo thiết bị DirectX 9 bảy lần. Lần nào cũng gặp `Mapping memory failed with
  VK_ERROR_MEMORY_MAP_FAILED` rồi `Memory allocation failed`: chương trình 32-bit không nhận được bộ nhớ đồ họa. "Tự sửa
  lỗi" nhận ra lỗi `dxvk-map-32` (bản 0.1.52). Game không có bản 64-bit. Driver Turnip không chạy trên Mali. Lần thử
  WineD3D cũng không vẽ được: Zink không khởi động trên driver Vulkan 1.1 này (`failed to load driver: zink`), nên không có
  OpenGL. Nhật ký riêng của game (`Game_ErrorLog.txt`) đầy dòng "次の文字がキャッシュできませんでした" (không tạo được
  ảnh cho chữ). Lần mở sau, game báo "前回の起動失敗を検知しました" (đã thấy lần mở trước bị lỗi).
- **Vì sao bộ nhớ không vào được:** chương trình 32-bit chỉ dùng được địa chỉ dưới 4 GB, mà driver Mali đặt bộ nhớ đồ
  họa ở địa chỉ cao. Wine (winevulkan của Proton 9 có `VK_EXT_map_memory_placed`) xin wrapper đặt bộ nhớ vào một chỗ dưới
  4 GB. Wrapper của app (`libvulkan_wrapper.so`, bản Pipetto, đọc mã máy) giả lập việc này:
  - nó tự xin bộ nhớ theo "Loại tài nguyên bộ nhớ" (`WRAPPER_RESOURCE_TYPE`): `auto` thử dma-heap/ION
    (`/dev/dma_heap/system`) rồi AHardwareBuffer; `dmabuf`, `ahb` và `opaque` chỉ dùng một cách;
  - rồi `mmap` file của bộ nhớ đó vào đúng địa chỉ Wine đưa (`MAP_FIXED`).

  Một bước trong đó hỏng trên máy này. Đến bản 0.1.54, wrapper không ghi gì ra, nên chưa biết bước nào.
- **Từ bản 0.1.55:** mỗi phiên Windows đặt `WRAPPER_LOG_LEVEL=error` và `WRAPPER_LOG_FILE=stdout`. Lỗi của wrapper
  vào đầu ra của Wine, và phiên giữ lại phần cuối trong `wine-cuoi.txt`. Các dòng có dạng `[error]: mmap failed: error
  <errno>`, `[error]: Failed to import dmaheap memory, res <mã Vulkan>` hay `[error]: Placed address/mapped address
  mismatch`. Khi mọi thứ chạy tốt, wrapper không ghi dòng nào. Nếu người chơi đã tự đặt `WRAPPER_LOG_LEVEL` thì app giữ
  nguyên.
- **Thử ngay, không cần bản mới:** trong cấu hình driver đồ họa của game ("Cấu hình driver đồ họa"), đổi "Loại tài
  nguyên bộ nhớ" từ `auto` sang `ahb`, rồi mở game. Vẫn đen thì thử `opaque`. Ghi lại kết quả: nếu một kiểu chạy được,
  "Tự sửa lỗi" có thể đề nghị kiểu đó khi gặp `dxvk-map-32`.
- **Thử máy:**
  1. The Clumsy Witch Seris trên vivo V2160 (bản 0.1.55, `auto`): "Gửi nhật ký" có `wine-cuoi.txt` với các dòng
     `[error]: …` của wrapper.
  2. Hồi quy: một game chạy tốt không có dòng `[error]:` nào trong `wine-cuoi.txt`, FPS như cũ.

## Bấm không ăn trong game Unity: nhật ký nói cú bấm đi đường nào (bản 0.1.55)

Nhật ký Female Anchor Next Door (Unity IL2CPP 64-bit, gói GameHub có `agvncheat.dll`; itel S666LN, Helio G99 MT6789,
Mali-G57 MC2; chuột Bluetooth "BT5.2 Mouse"; bản 0.1.54; 19/10/2026): "click không ăn". Ba phiên, game vẽ bình thường
(cửa sổ DXVK 818×395). Lúc mở có 43 giây đứng hình (game nạp).

- **Nhật ký không đủ để biết vì sao:**
  - App bật "Chạm gửi như chuột thật, có raw input" (game dùng Unity Input System): chạm và chuột đi qua
    `agvn-winhandler.exe` (SendInput), vì Input System chỉ đọc chuột từ raw input. Khi `agvn-winhandler.exe` chưa báo sẵn
    sàng, khi chuột tương đối hay "Tắt chuột" đang bật, cú bấm đi bằng sự kiện X, và game không thấy. Nhật ký chỉ có dòng
    "Chuột BT5.2 Mouse: app nhận cú bấm (app đang giữ chuột)", không nói cú bấm đi đường nào.
  - Game được thêm bằng lối tắt riêng, không qua "Thêm game", nên app không lưu engine ("engine chưa rõ"). Vì vậy
    `Player.log` của Unity không được lấy. Đây là nơi Unity ghi lỗi khi một cú bấm tới được game mà nút không chạy.
- **Từ bản 0.1.55:**
  - `su-kien.txt` ghi cú bấm đầu tiên của mỗi phiên (chạm hay chuột): "Cú bấm đầu tiên tới game qua agvn-winhandler.exe
    (raw input)", hoặc "Cú bấm đầu tiên đi bằng sự kiện X, không có raw input: …" kèm lý do (`agvn-winhandler.exe` chưa
    báo sẵn sàng, chuột tương đối, "Tắt chuột"). Chỉ khi chế độ raw input đang bật cho game.
  - Game chưa lưu engine thì app lấy engine theo thư mục của file chạy (`GameExeResolver.detectEngine`) để chép nhật ký
    engine (`Player.log` của Unity...) và để theo dõi crash của Unity. `su-kien.txt` ghi "Engine theo thư mục game: …".
    Các cài đặt khác theo engine (chất lượng Unity, giảm RAM) vẫn chỉ áp dụng cho game thêm qua "Thêm game".
- **Test:** `AgvnEngineLogsCopyTest.aGameWithoutAStoredEngineGetsTheOneItsFolderShows`.
- **Thử máy:**
  1. Female Anchor Next Door (bản 0.1.55): bấm vài lần ở màn đầu bằng chạm và bằng chuột, rồi "Gửi nhật ký". Nhật ký có
     dòng "Cú bấm đầu tiên …" và `Player.log` của game.
  2. Hồi quy: game Unity đã chạy tốt (như Open At Nine) vẫn nhận chạm như trước; nhật ký ghi "qua agvn-winhandler.exe
     (raw input)".

## Game nạp lâu bị dừng khi rời app, và game tự thoát sau khi nạp script (bản 0.1.59)

Nhật ký The Agnietta (Pixel Game Maker MV: cocos2d-x 3.17.1, `mozjs-33.dll`, `libvlc`, 32-bit; bản Việt hoá có
cheat; Galaxy Z Fold4 SM-F936N, Snapdragon 8+ Gen 1, Adreno 730; Proton 9, OpenGL qua freedreno; 0.1.57 và 0.1.58;
19/10/2026): "crash và out". Năm phiên, không phiên nào có cửa sổ game.

- **Game cần khoảng 56 giây chạy liên tục mới mở cửa sổ:** `action_log.txt` của game ghi `projectFilePath` ngay khi
  mở, rồi 56 giây sau mới tới `InputManager Init`. Trong lúc đó FPS là 0 và một luồng bận 41–84% một nhân.
- **Bốn phiên bị dừng trước mốc đó:** người chơi rời app khi game đang nạp. Winlator dừng các tiến trình Wine khi app
  xuống nền (`ProcessHelper.pauseAllWineProcesses`), nên thời gian ở ngoài app không được tính. Thời gian game thật sự
  chạy là khoảng 55, 33, 43 và 35 giây. Ba phiên kết thúc bằng vuốt tắt app. Phiên cuối (0.1.58) còn đang nạp lúc gom
  nhật ký.
- **Phiên còn lại tự thoát:** phiên 22:00:58 chạy được khoảng 58 giây. Game khởi tạo OpenGL (`FD725`, OpenGL 3.3 Mesa
  26.3) và tới `# ScriptingCore Start` (máy JavaScript SpiderMonkey). Một giây sau Wine kết thúc.
  - Không có dòng "Unhandled exception" hay báo cáo của winedbg. Wine in các dòng này dù `WINEDEBUG` đặt thế nào
    (`AgvnWineTail`), nên game không crash theo kiểu thường. Nó tự thoát hoặc tự đóng.
  - "Mã thoát 0" là của `wine explorer` mở game, không phải của game, nên không nói được game kết thúc thế nào.
  - Vì sao game thoát thì nhật ký này chưa đủ để biết.
  - Không nơi nào ghi phần 32-bit chạy bằng gì. `moi-truong.txt` được ghi trước khi bộ khởi chạy đặt `HODLL`, và logcat
    cắt dòng biến môi trường ở khoảng 4000 ký tự, trước `HODLL`. Theo mặc định là FEXCore 2601 (logcat có
    `fexcoreVersion in use: 2601`).
- **Từ bản 0.1.59:**
  - **Game đang khởi động nạp tiếp khi người chơi rời app (`AgvnLoadAway`).** "Đang khởi động" là lúc game chưa vẽ
    khung nào (`AgvnStartupProgress`).
    - Rời app lúc đó thì Wine không bị dừng. `su-kien.txt` ghi "Rời app khi game còn đang khởi động: game nạp tiếp…".
    - Khi game lên hình mà người chơi vẫn ở ngoài, game dừng ngay như trước: "Game đã lên hình khi người chơi ở ngoài
      app". Nếu sau 15 phút game vẫn chưa lên hình thì cũng dừng.
    - Game đã lên hình thì rời app vẫn dừng ngay như trước. Ảnh thu nhỏ (picture-in-picture) không đổi.
    - Chỉ tốn pin lúc game đang nạp ở nền, là lúc người chơi đang chờ nó.
  - **`su-kien.txt` ghi bộ giả lập (`AgvnEmulatorNote`):** "Giả lập 32-bit: FEXCore 2601 (libwow64fex.dll)" hoặc
    "WOWBox64 0.4.2 (wowbox64.dll)" với Wine arm64ec, và "Giả lập x86: Box64 …" với Wine x86-64.
- **Test:** `AgvnLoadAwayTest`.
- **Việc tiếp cho The Agnietta (thử tay):**
  1. Bật "Bật debug Wine" rồi mở game. **Giữ app trên màn hình** ít nhất 2 phút, tới khi game lên hình hoặc tự thoát.
     Rồi "Gửi nhật ký": Wine sẽ ghi cảnh báo và lỗi của mọi kênh lúc game thoát.
  2. Nếu game vẫn thoát sau `ScriptingCore Start`: cài đặt game → "Giả lập 32-bit" đổi FEXCore sang WOWBox64, rồi thử
     lại. Máy JavaScript của game tự sinh mã x86 lúc chạy, và hai bộ giả lập dịch phần mã đó khác nhau.
- **Thử máy (0.1.59):**
  1. Mở một game Windows nạp lâu (The Agnietta), rời app ngay khi màn còn đen, chờ 1–2 phút rồi quay lại: game đã
     qua bước nạp. `su-kien.txt` có "Rời app khi game còn đang khởi động" và, nếu game lên hình lúc anh ở ngoài, "Game
     đã lên hình khi người chơi ở ngoài app". Dòng "Giả lập 32-bit: …" có ở đầu phiên.
  2. Hồi quy: rời app khi game đã chạy, game dừng ngay. Quay lại, game chạy tiếp như trước.

## DLL cheat cạnh file chạy thiếu hàm Wine cần; công cụ tên tiếng Nhật thành game riêng (bản 0.1.60)

Báo cáo ngày 20/10/2026 (コトネイロ, bản có bảng cheat): game không mở được trên điện thoại.

- **Vì sao:**
  - Wine báo `unimplemented function version.dll.GetFileVersionInfoSizeW` rồi game dừng ngay khi mở.
  - `version.dll` cạnh EXE là của bảng cheat. Nó chỉ chuyển tiếp 3 hàm game gọi tới `version.dll` của hệ thống.
  - App nạp DLL cạnh EXE trước bản của Wine (`GameDllOverrides`: native trước). `mmdevapi`, phần âm thanh của Wine,
    cũng dùng `version.dll`, nên nó lấy bản của cheat và thiếu `GetFileVersionInfoSizeW`. Trên Windows, `mmdevapi` không
    gọi hàm này nên cheat vẫn chạy được trên PC.
  - Phía game đã sửa: `version.dll` của cheat chuyển tiếp đủ 17 hàm của `version.dll` hệ thống.
- **Từ bản 0.1.60, "Tự sửa lỗi" nhận ra lỗi này (`dll-function`):**
  - Dấu hiệu là dòng "Unimplemented function X.dll.Y called" của Wine, hoặc của winedbg.
  - Nếu `X.dll` nằm cạnh file chạy của game, app có nút "Dùng X.dll của Wine thay bản đi kèm game" (`dll-wine`,
    `AgvnDllProxy`): game chạy lại với bản DLL của Wine. Cheat hoặc mod trong file đó không chạy, file của game giữ
    nguyên.
  - Nếu chính DLL của Wine thiếu hàm thì app không có nút này, chỉ có "Gửi nhật ký".
  - Mỗi game chỉ được đề nghị một lần. Cách sửa này dùng chung cách của "mods-off" (`AgvnMods.withBuiltin`).
- **Hai game trong một thư mục:**
  - Thư mục có `コトネイロ.exe` (game) và `エンジン設定.exe` ("cài đặt engine", công cụ của hãng), nên app nhận ra 2
    game.
  - Tên của công cụ đi kèm (`GameExeResolver.HELPERS`) trước đây chỉ có chữ tiếng Anh: launcher, setup, config,
    setting… Giờ có thêm 設定, 设置, 設置, コンフィグ, ランチャー, セットアップ, アンインストール, 启动器, 卸载, 설정.
  - Công cụ PC (`tools/agvn/agvn_profile_lib.py`, `HELPER_PARTS`) cũng vậy. Bản cũ còn chọn nhầm `エンジン設定.exe` làm
    file chạy, vì エ đứng trước コ khi xếp tên.
- **Test:** `AgvnDoctorTest.aDllThatLacksAFunctionWineCalls`, `GameExeResolverTest.aToolNamedInAsianLanguagesIsNotASecondGame`
  (phần dùng thư mục thật cần JVM lưu được tên Unicode: Windows, hoặc Linux với `LANG=C.UTF-8`) và
  `tools/agvn/tests/test_toolkit.py`.
- **Thử máy:**
  1. Game có `version.dll` cheat cũ (3 hàm): mở game, game dừng. Về thư viện, "Tự sửa lỗi" hỏi "File version.dll thiếu
     hàm", có nút "Dùng version.dll của Wine…". Bấm nút: game mở được, cheat không có.
  2. Thêm thư mục コトネイロ: app chỉ nhận một game, file chạy là `コトネイロ.exe`.

## App tự đóng sau vài phút chơi: "Drawable data initialized as null!" (bản 0.1.62)

Nhật ký ngày 20/10/2026 (The Blood of Yidhra, Unity 2020.1, Xiaomi 23117RK66C, Adreno 750, bản 0.1.61): app tự đóng
sau 7 phút ở phiên đầu, 5 phút ở phiên sau.

- **Hiện tượng:**
  - Cả hai phiên đều kết thúc bằng "App bị lỗi Java (CRASH)". App văng chứ không phải game, và logcat có cùng một lỗi:
    `IllegalStateException: Drawable data initialized as null!` lúc X server của app tạo con trỏ chuột cho game
    (`CursorRequests.createCursor` → `CursorManager.createCursor` → `new Drawable`).
  - `Player.log` của Unity không có lỗi nào lúc đó. Game vẫn đang chạy.
  - Ở cả hai phiên, DMA-BUF cao nhất gần như bằng nhau: 697 MB và 694 MB. Đó là dấu hiệu chạm một giới hạn cố định,
    không phải thiếu RAM: RAM trống thấp nhất vẫn còn 1,7–1,9 GB.
- **Vì sao:**
  - Mỗi ảnh (pixmap) và mỗi con trỏ của game có một bộ đệm đồ hoạ Android (`AHardwareBuffer`, `Drawable.allocate`).
  - X server của Winlator gốc không bao giờ trả bộ đệm này. Game xoá pixmap (FreePixmap) hay con trỏ (FreeCursor) thì
    app chỉ bỏ đối tượng Java, còn bộ đệm vẫn bị giữ tới khi app đóng.
  - Wine tạo con trỏ mới cho mỗi HCURSOR mới: 2 pixmap (ảnh và mặt nạ) cộng ảnh của con trỏ, tức 3 bộ đệm. Game này
    đổi con trỏ liên tục, nên bộ đệm cứ tăng. Tới lúc Android từ chối cấp bộ đệm mới, `allocate` trả 0, `Drawable` ném
    lỗi, và lỗi này không được bắt nên cả app đóng.
- **Sửa (bản 0.1.62):**
  - `Drawable.release()` trả bộ đệm `allocate` đã lấy (`AHardwareBuffer_release`, `drawable.c`). Nó chạy trong
    `renderLock` và đặt `backingAHB` về 0, nên bộ vẽ nào chép ảnh sau đó sẽ thấy 0 và bỏ qua.
  - **Pixmap:** trả bộ đệm khi game xoá pixmap (`PixmapManager.freePixmap`).
    - Ảnh của DRI3 (khung hình của DXVK) là của `GPUImage`. Chỉ bộ đệm drawable tự lấy được trả, `GPUImage` giữ nguyên.
    - Bộ vẽ Vulkan đã tự giữ tham chiếu riêng cho ảnh nó dùng, nên không bị ảnh hưởng.
    - Biểu tượng cửa sổ (`Pixmap.toBitmap`, trình quản lý tác vụ đọc ở luồng riêng) giờ đọc trong `renderLock`.
  - **Con trỏ:** X giữ con trỏ đã bị xoá khi còn cửa sổ đang hiện nó. Wine xoá con trỏ trên một kết nối khác với kết
    nối đổi con trỏ cho cửa sổ, nên FreeCursor có thể tới trước.
    - `AgvnUseCount` đếm số cửa sổ đang hiện con trỏ (`WindowAttributes`, cửa sổ bị huỷ). Ảnh của con trỏ được trả khi
      con trỏ đã bị xoá và không cửa sổ nào hiện nó nữa.
    - Bộ vẽ EGL và DisplayX giữ tham chiếu riêng (`AHardwareBuffer_acquire` trong `nativeCreateCursor`) tới khi tự gỡ
      con trỏ (`CursorManager::removeCursor`).
    - Bộ vẽ Vulkan chép điểm ảnh của con trỏ trong `renderLock`, và bỏ qua con trỏ đã trả ảnh.
    - `FreeCursor` cho một con trỏ không có giờ không làm gì. Trước đây nó gây lỗi `NullPointerException` ở bộ vẽ EGL.
  - **Không đóng app nữa:** Android không cấp bộ đệm cho yêu cầu nào (tạo pixmap, cửa sổ, con trỏ) thì game nhận lỗi
    X `BadAlloc` cho yêu cầu đó (`Drawable.NoBuffer`, `XClientRequestHandler`), thay vì cả app đóng.
    - `su-kien.txt` ghi "Android không cấp bộ đệm đồ hoạ cho yêu cầu X N (cỡ, số bộ đệm đang giữ)…", tối đa một dòng
      mỗi phút (`AgvnBufferNote`).
    - Wine thường thoát game khi nhận lỗi X không lường trước. Khi đó game đóng nhưng app vẫn mở.
  - **Còn lại:** bộ đệm của cửa sổ vẫn chưa được trả khi cửa sổ bị huỷ hoặc đổi cỡ. Bộ vẽ EGL và DisplayX còn dùng con
    trỏ thô tới bộ đệm này trên luồng vẽ, nên cần sửa riêng. Game ít khi tạo và huỷ cửa sổ liên tục.
- **Test:** `AgvnBufferReleaseTest`: cách đếm của `AgvnUseCount`, và các chỗ móc trả bộ đệm trong mã nguồn (Java và
  native). Bộ đệm là của Android nên cloud không chạy thử được.
- **Thử máy:**
  1. The Blood of Yidhra: chơi quá 10 phút, rê và bấm nhiều để game đổi con trỏ. App không đóng.
     `adb shell dumpsys meminfo com.agvn.player` phần DMA-BUF (hoặc dòng RAM của `tom-tat.txt`) không tăng mãi.
  2. Game Unity, RPG Maker, Ren'Py (Wine) khác: con trỏ, biểu tượng trong trình quản lý tác vụ, menu chuột phải vẫn
     như cũ.
  3. Chọn Renderer là EGL, rồi DisplayX (cài đặt game hoặc container): đổi con trỏ nhiều lần, thoát game, mở lại. App
     không văng.

## Game khoá chết khi nạp cảnh (bản 0.1.63)

Phiếu ngày 21/10/2026 (`PHIEU-LOI-AGVN-PLAYER-UNITY-IL2CPP-KHOA-CHET-KHI-NAP-CANH.md`): game Unity 2022.3 IL2CPP (DX11),
có gói Addressables mã hoá nạp qua `AssetBundle.LoadFromStreamAsync` với Stream giải mã viết bằng C#. App 0.1.62,
Proton 9 arm64ec, FEXCore 2601, POCO F8 Pro. Màn tiêu đề và đoạn mở đầu chạy được; tới lúc nạp cảnh chơi chính thì màn
hình đen mãi (FPS 0, luồng bận nhất 3% một nhân), 2/2 lần, cùng một chỗ. Cùng thư mục game chạy qua trên GameHub
(Proton 11 arm64x, FEX `HalfBarrierTSO=0`) và trên PC.

- **Nhật ký Wine:** luồng chính chờ một khoá (critical section không tên, khoá của IL2CPP) do một luồng
  "IL2CPP Threadpool worker" vừa tạo giữ. Khoá không bao giờ được nhả; mỗi phút Wine báo
  `RtlpWaitForCriticalSection … wait timed out in thread 00e0, blocked by 0224, retrying (60 sec)`. Ba luồng khác
  chờ khoá thứ hai. Game không bị giết, máy còn 2,9 GB RAM trống.
- **Cách Wine báo:** luồng chờ khoá quá 5 giây thì Wine in dòng trên, rồi in lại mỗi 60 giây khi vẫn chờ
  (`ntdll/sync.c`). Lần đầu có thể chỉ là nạp lâu; lần thứ hai cho cùng luồng và cùng khoá nghĩa là luồng đã chờ hơn
  65 giây.
- **App nhận ra (`AgvnLockWatch`):**
  - Kênh `err+sync` có trong WINEDEBUG khi nhật ký Wine tắt (`AgvnWineDebug.QUIET_SPEC`). Kênh này chỉ in khi có luồng
    chờ khoá quá lâu hoặc khoá hỏng.
  - Lần báo thứ hai cho cùng luồng và khoá, app ghi `[AGVN] Khoá chết: luồng … chờ khoá … hơn 1 phút, khoá do luồng …
    giữ` vào dòng cuối của Wine (không bị gộp vào dòng lặp lại) và vào `su-kien.txt`, mỗi khoá một lần trong một phiên.
  - "Tự sửa lỗi" có lỗi mới `deadlock` (`"when": []`): hỏi sau khi người chơi thoát game đang đứng, cả khi vuốt tắt app
    (đọc dòng cuối Wine đã giữ lại). Một lần chờ 5 giây (nạp lâu) không bị hỏi.
- **Cách sửa (theo thứ tự, mỗi lần một cách, cách không giúp thì trả lại; `AgvnLockFixes`):**
  1. **Giả lập CPU giữ đúng thứ tự bộ nhớ** (`emulator-order`): `FEX_HALFBARRIERTSOENABLED=0` trong biến môi trường của
     game. FEX mặc định đổi mã gặp truy cập bộ nhớ không thẳng hàng sang kiểu "half-barrier", và chính FEX ghi rằng
     cách này "có thể nguy hiểm vì truy cập thẳng hàng qua cùng đoạn mã đó không còn nguyên tử". Khoá của IL2CPP đọc qua
     đoạn mã như vậy có thể thấy giá trị cũ. GameHub chạy FEX với giá trị 0. Chỉ hiện khi FEX chạy mã của game (Wine
     arm64ec, hoặc giả lập 32-bit là FEXCore) với TSO bật. Cái giá: mã hay đụng dữ liệu không thẳng hàng chạy chậm hơn.
  2. **Tắt Esync** (`esync-off`): `WINEESYNC=0`. Wine chờ và khoá qua wineserver thay vì esync. Nhật ký của phiếu có hơn
     4.000 dòng `esync:get_object Failed to retrieve fd`. Cái giá: game nhiều luồng chờ lâu hơn một chút.
  3. Giả lập CPU ổn định hơn, chạy bằng Wine cũ (Proton 9, cho game đang chạy Proton 10), chạy không có mod, gửi
     nhật ký.
  - Hai cách mới cũng có trong "Game đứng hình, treo", sau giả lập CPU ổn định hơn và chạy không có mod.
  - Không bật cho mọi game: cả hai chậm hơn, nên chỉ đề nghị khi game đã khoá chết hoặc người chơi báo đứng hình.
- **Chưa làm:** chuyển game Proton 9 sang Proton 10 từ "Tự sửa lỗi" (cách 3 của phiếu). Đường đó cần cài Proton 10 lần
  đầu (khoảng 1 phút, 900 MB) như thanh phim (`AgvnMovieWatch`). Proton 11 chưa có trong app.
- **Test:** `AgvnLockWatchTest` (dòng thật của phiếu, ghi chú trong dòng cuối Wine, lỗi `deadlock` và thứ tự cách sửa,
  biến môi trường của hai cách); `AgvnWineDebugTest`.
- **Thử máy:**
  1. Game của phiếu (Proton 9): vào tới lúc nạp cảnh, chờ màn hình đen hơn 1 phút. `su-kien.txt` có
     `Khoá chết: luồng … chờ khoá …`. Thoát game: thư viện hỏi "Game bị khoá chết, đứng hình", nút đầu là "Giả lập CPU
     giữ đúng thứ tự bộ nhớ". Bấm, mở lại: game qua được cảnh đó thì giữ; vẫn đứng thì app trả lại và mời "Tắt Esync".
  2. `moi-truong.txt` của lần chạy sau có `FEX_HALFBARRIERTSOENABLED=0` (hoặc `WINEESYNC=0`).
  3. Hồi quy: game Unity, Unreal, Ren'Py đang chạy tốt, thoát bình thường: không bị hỏi gì. `wine-cuoi.txt` có thể có
     một dòng `err:sync` khi game nạp lâu, nhưng không có `[AGVN] Khoá chết`.

## RPG Maker VX Ace: viewport đã huỷ, lỗi script và đường Windows (bản 0.1.63)

Báo cáo ngày 21/10/2026 (`BAO-CAO-LOI-CHUNG-AGVN-PLAYER-RGSS-VXACE.md`, POCO F8 Pro). Một game VX Ace có script hiệu ứng
ánh sáng bên thứ ba, chạy bằng Chạy nhẹ (mkxp-z), văng ở lần chuyển cảnh đầu với hộp lỗi
`Script '…' line N: RGSSError occurred. disposed viewport`. Trên Windows (RGSS3 gốc) game chạy bình thường.

- **Vì sao (đọc mã mkxp-z, commit ghim `095b9cc3`):**
  - Script giữ khoảng 20 sprite hiệu ứng trong một danh sách của `$game_temp`. Khi game tạo lại `$game_temp` (ván mới,
    tải save), không ai huỷ các sprite đó, còn viewport của `Spriteset_Map` thì đã huỷ.
  - Mỗi khung hình, `Graphics.update` và `Graphics.transition` gọi `prepare()` của mọi sprite còn sống (tín hiệu
    `prepareDraw`).
  - `Sprite::prepare` hỏi `viewport->getVisible()`. Viewport đã huỷ trả lời bằng `RGSSError: disposed viewport`
    (`aboutToAccess` → `guardDisposed`), và lỗi đó đi ra tới script của game.
  - RGSS3 thì thôi vẽ sprite đó và không báo gì.
  - Plane, Window, WindowVX, Tilemap không hỏi viewport khi chuẩn bị vẽ, nên chỉ Sprite có lỗi này.
- **Sửa trong mkxp-z (`patches/0006-sprite-disposed-viewport.patch`):** sprite có viewport đã huỷ được coi là không
  hiện. Lệnh Ruby gọi thẳng vào viewport đã huỷ (`vp.rect`, `vp.ox = …`) vẫn báo lỗi như RGSS. Viewport C++ chưa bị
  giải phóng khi sprite còn sống, vì đối tượng Ruby của sprite giữ tham chiếu tới viewport (biến `@viewport`).
  `libmkxp-z.so` dựng lại, SHA-256 mới ghi trong `scripts/agvn/pins.txt`.
- **Hộp lỗi của Chạy nhẹ (`AgvnRgssFailure.ask`):**
  - Trước đây nút chính là "Game bị lỗi? Chạy bằng Windows". Người chơi bấm như bấm OK, game chuyển hẳn sang Wine,
    rồi kẹt ở màn đen (lỗi `RGSS301.dll` ở mục "DLL ở bộ nhớ chung" bên trên). App không tự chuyển: người chơi đã chọn
    nút đó.
  - Giờ lỗi script có chỗ dừng (`Script '…' line N`, hoặc dòng backtrace `:N:in`) mà không nhắc DLL (Win32API,
    `.dll`) hay RTP còn thiếu thì:
    - lời nhắn nói script của game dừng, và chạy bằng Windows thường cũng dừng ở cùng chỗ;
    - nút chính là "Gửi nhật ký cho AGVN";
    - "Thử chạy bằng Windows" là nút phụ.
    Gửi nhật ký xong, hộp lỗi hiện lại để chọn tiếp.
  - Lỗi khác (DLL qua Win32API, thiếu RTP, mkxp-z không mở được game) giữ "Chạy bằng Windows" làm nút chính, thêm nút
    "Gửi nhật ký".
- **`tim-tep.txt` (`agvn_files.rb`):** game thử ảnh có hậu tố ngôn ngữ trước (`Title_en`, `Title_`) rồi mới dùng ảnh
  thường. Khi ảnh thường có trong thư mục, lần thử đó không còn được ghi là "Không có".
- **Test:** `AgvnRgssFailureTest`; `ruby scripts/agvn/mkxp-z/test-agvn-files.rb` (ca `Title_en`, `Title_`,
  `Other_en`). Bản vá mkxp-z cần thử trên máy.
- **Thử máy:**
  1. Game VX Ace của báo cáo, Chạy nhẹ: qua lần chuyển cảnh đầu, vào màn chơi, tải save; không có hộp
     "disposed viewport".
  2. Đoạn tái hiện của báo cáo (dán trước Main: `Viewport.new`, `Sprite.new(vp)`, `vp.dispose`, `Graphics.freeze`,
     `Graphics.transition(10)`, `Graphics.update`), thêm một `Plane.new(vp)` và một `Window_Base` có `viewport = vp`:
     không có hộp lỗi.
  3. Một game có lỗi script thật (thêm dòng `nil.width` vào một script): hộp lỗi có nút chính "Gửi nhật ký cho AGVN",
     nút phụ "Thử chạy bằng Windows". Gửi nhật ký xong, hộp lỗi hiện lại.
  4. Hồi quy: game RPG Maker XP, VX, VX Ace đang chạy tốt bằng Chạy nhẹ vẫn chạy như trước.

## Giả lập 32-bit không chạy mã của DLL đi kèm game (The Agnietta, bản 0.1.63)

Nhật ký ngày 21/10/2026 (Redmi Note 9 Pro, Snapdragon 720G, Proton 9 arm64ec, FEXCore 2601, Wine debug bật). The Agnietta
(Pixel Game Maker MV, 32-bit) nạp khoảng 80 giây, một nhân chạy 80%, lên được OpenGL (`ScriptingCore Start`) rồi văng:

```
Unhandled exception: page fault on execute access to 0x79811e80 in wow64 32-bit code (0x79811e80).
=>0 0x79811e80 in msvcr100 (+0x1e80)
  1 0x7994d8ea in sqlite3 (+0x6d8ea)
0x79811e80 msvcr100+0x1e80: push %ebp
```

- **Vì sao:**
  - Game gọi SQLite; `sqlite3.dll` gọi một hàm của `msvcr100.dll` đi kèm game.
  - Địa chỉ đó là mã thật của `msvcr100.dll` (winedbg đọc được `push %ebp`). CPU thật chạy được mã ấy.
  - Ngay trước đó Wine ghi `EXCEPTION_ACCESS_VIOLATION`, backtrace ở `libwow64fex.dll + 0xE9024`, cùng chỗ với lỗi
    `RGSS301.dll` của báo cáo VX Ace. Tức là chính FEX báo lỗi: FEX chỉ dịch mã ở những trang nó được báo là chạy được
    (`Decoder::CheckRangeExecutable` của FEXCore). Trang này nó coi là không chạy được.
  - Vì sao trang ấy mất quyền chạy thì chưa rõ. Mọi DLL của game được Wine đọc chép vì thư mục game ở bộ nhớ chung
    (`map_pe_header noexec file system, falling back to read`). Các DLL khác vẫn chạy.
- **Sửa (Tự sửa lỗi, `AgvnEmulatorNoExec`):** nhận ra lỗi qua điều kiện `module-code`: game văng vì "page fault on
  execute access" trong mã 32-bit wow64, và khung đầu của backtrace ở đúng địa chỉ đó, bên trong một module.
  - Nếu module là thư viện C của Microsoft (msvcr*, msvcp*, vcruntime*, ucrtbase, concrt*), lỗi là
    `emulator-noexec-crt`. Các cách sửa, theo thứ tự:
    1. "Dùng msvcr100.dll của Wine thay bản đi kèm game" (`dll-wine`);
    2. "Chạy phần 32-bit của game bằng WOWBox64 thay FEXCore" (`emulator-wowbox64`, đặt "Giả lập 32-bit" của game là
       `Box64`, tức `wowbox64.dll`);
    3. giả lập ổn định;
    4. gửi nhật ký.
  - Module khác (`emulator-noexec`): WOWBox64, giả lập ổn định, gửi nhật ký.
  - Chỉ đề nghị khi lỗi này hiện, cho game đang chạy FEXCore với Wine arm64ec. Game khác không đổi gì.
  - Giới hạn: các lần chạy tắt debug Wine (12:06 cùng ngày, và 19/10 trên Snapdragon 8+ Gen 1) cũng dừng 1–2 giây sau
    `ScriptingCore Start`, nhưng không in báo cáo crash nào. Có thể `Game.exe` (nạp `dbghelp`) tự bắt lỗi. Khi đó app
    không thấy dấu hiệu nào, nên chỉ nhận ra lỗi này khi Wine in báo cáo, như lần bật debug.
- **Lần chạy chỉ hiện màn nạp không còn là "chạy tốt" (`AgvnEvidence.loadingOnly`):**
  - Trước đây, lần 12:06 (màn nạp 5 giây rồi không vẽ gì, 110 giây sau game tự tắt) và các lần người chơi thoát sau
    30 giây đều được lưu là cấu hình chạy tốt. Vì vậy lần văng sau bị báo nhầm "Game bị lỗi với cấu hình mới".
  - Giờ game chỉ vẽ trong chưa tới 10 giây (từ khung đầu tới khung cuối) rồi không vẽ gì ít nhất 10 giây tới lúc tắt
    thì lần chạy đó không được lưu là tốt.
- **Test:** `AgvnEmulatorNoExecTest`.
- **Thử máy:**
  1. The Agnietta trên máy yếu (Redmi Note 9 Pro), chờ hết màn nạp (khoảng 1 phút rưỡi): game văng như cũ, Tự sửa lỗi
     hiện "Giả lập 32-bit không chạy được mã của game" với nút dùng `msvcr100.dll` của Wine.
  2. Bấm thử: game qua được chỗ văng, hoặc app đề nghị tiếp WOWBox64. `su-kien.txt` ghi
     "Giả lập 32-bit: WOWBox64 …".
  3. Hồi quy: game 32-bit đang chạy tốt bằng FEXCore không bị hỏi gì. Game văng vì lỗi khác vẫn ra "Game bị lỗi và tự
     tắt" như trước.

## Danh sách cửa sổ khi người chơi báo lỗi (To Be or Not to Be, bản 0.1.63)

Nhật ký ngày 21/10/2026 (Xiaomi 25053PC47G, Adreno 825). "To Be or Not to Be" (Unity IL2CPP, cửa sổ do UniWindowController
lo; bản Việt hoá có mod `tbntbvn.dll`, chặn nền trong suốt bằng file `KHONG-TRONG-SUOT.txt`) màn hình đen dù game vẫn
gửi 30 khung/giây:

- Game vẽ bằng DXVK vào cửa sổ 1272×694 (cửa sổ có viền, phóng to trong màn hình Wine 1288×746).
- Từ giây thứ 20 có một cửa sổ 1×1, rồi cứ 5 giây lại có nội dung mới cho một cửa sổ to bằng cả màn hình (1288×746).
- Lần chơi "hết chập chờn" trước đó (20:14:55) thì khác: cửa sổ game 1288×728, và cứ 5 giây game tạo lại swapchain một
  lần (1280×720, 1296×754, rồi 1288×728). Vậy chập chờn là do cửa sổ đổi cỡ, không hẳn do LRZ.
- App vẽ mọi cửa sổ như không trong suốt (`window.frag`, `useTexAlpha = 0`). Một cửa sổ sâu 32 bit vốn trong suốt sẽ
  hiện thành màu đen, che game. Nhật ký chưa cho biết cửa sổ nào nằm trên cùng.
- **Thêm (`AgvnWindowList`):** khi người chơi chọn một lỗi trong ⋮ → Tự sửa lỗi lúc game đang chạy, `su-kien.txt` ghi
  các cửa sổ đang hiện, từ dưới lên trên: id, tên chương trình, tiêu đề, vị trí, cỡ, độ sâu màu (24 hay 32).
- **Thử máy:** khi màn hình đen, mở ⋮ → Tự sửa lỗi → "Màn hình đen dù game vẫn chạy", rồi gửi nhật ký. Trong
  `su-kien.txt` có dòng "Cửa sổ trên màn hình, dưới lên trên".
