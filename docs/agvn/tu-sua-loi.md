# Tự sửa lỗi

Khi game Windows gặp lỗi hoặc chạy chậm, app tự nhận ra lỗi và hỏi người chơi theo mẫu: "Máy của bạn đang bị…
Bạn có thể…". Mỗi cách sửa là một nút. Bấm nút là app đổi cấu hình của riêng game đó rồi mở lại game. Người chơi
không cần vào "Cấu hình".

## Khi nào app hỏi

| Lúc | Ở đâu | Ví dụ |
|---|---|---|
| Trước khi vào game | Hộp hỏi | Tiết kiệm pin đang bật: "Mở cài đặt Tiết kiệm pin" hoặc "Chơi luôn". |
| Đang chơi | Thanh trên game; game vẫn chạy bên dưới | Game chậm đều dưới 20 FPS suốt 1 phút. Cách sửa có tác dụng ở lần mở game sau. |
| Game vừa thoát hoặc bị lỗi | Hộp hỏi ở thư viện, sau khi app khởi động lại | Game tắt ngay khi mở, crash, thiếu DirectX, thiếu file `.dll`. Bấm một cách sửa là game mở lại ngay. |
| Android đã tắt app | Hộp hỏi ở lần mở app tiếp theo (trong 24 giờ) | Máy hết RAM; HyperOS tắt app khi chuyển sang app khác. |

Mỗi lỗi chỉ hỏi một lần. Cách sửa đã thử mà vẫn lỗi thì lần sau không hiện lại, cho đến khi game chạy tốt một
lần. Hộp game chậm có nút "Để vậy, không hỏi lại" cho riêng game đó.

## Lỗi app nhận ra (`assets/agvn/game-problems.json`)

| Lỗi | App nhận ra nhờ | Cách sửa (theo thứ tự) |
|---|---|---|
| Game cần Steam | Wine báo thiếu `steam_api.dll` | Gửi nhật ký |
| Thiếu file `.dll` | Wine: `Library X.dll (which is needed by …) not found` | Bật thư viện Windows chứa file đó (DirectX, XAudio, VC++ 2010), gửi nhật ký |
| File `.dll` hỏng hoặc sai 32/64-bit | Wine: lỗi `c000007b` | Gửi nhật ký |
| Game cần .NET | Wine: `Wine Mono is not installed` | Gửi nhật ký |
| Godot không mở được OpenGL | Câu lỗi của Godot, hoặc game Godot tắt trước khi hiện hình | Chạy Godot bằng Vulkan (Godot 4) hoặc GLES2 (Godot 3), đổi driver |
| Không tạo được DirectX / thiếu DirectX 11 | Câu lỗi của DXVK, Unity (`InitializeEngineGraphics failed`) | Đổi bản DXVK, đổi driver (Turnip ↔ System), dùng WineD3D |
| Driver đồ họa lỗi giữa chừng | `VK_ERROR_DEVICE_LOST` | Để Turnip tự chọn chế độ dựng hình, đổi driver, đổi DXVK, hạ Đồ họa |
| Hết bộ nhớ đồ họa / hết RAM | DXVK, Unity, Unreal báo hết bộ nhớ | Hạ Đồ họa |
| File game hỏng | Unity: `is corrupted` | Gửi nhật ký (cần chép lại game) |
| Máy hết RAM nên Android tắt app | Lý do Android ghi lại (`LOW_MEMORY`) | Hạ Đồ họa |
| Máy tắt app khi chạy nền | Lý do Android ghi lại (`SIGKILL` khi app chạy nền) | Mở cài đặt pin của AGVN |
| Game không chịu độ phân giải nhỏ ("The current resolution is too low") | Game tắt trước khi hiện hình, màn hình dưới 480 dòng | Nâng Đồ họa (thường là Thấp 854×480), dùng lại cấu hình đã chạy được |
| Game không chạy với cấu hình mới | Lần trước chạy được, đổi cấu hình xong thì tắt trước khi hiện hình | Dùng lại cấu hình đã chạy được, về cấu hình gốc |
| Game crash | Wine in báo cáo crash | Đổi DXVK, giả lập CPU ổn định hơn, đổi driver, về cấu hình gốc |
| Game tắt ngay, không rõ lỗi | Game tắt trước khi hiện hình | Về cấu hình gốc, đổi DXVK, đổi driver, gửi nhật ký |
| Game chậm vì GPU | Dưới 20 FPS, GPU từ 85% trở lên | Hạ Đồ họa, thử chế độ dựng hình Gmem, để Turnip tự chọn |
| Game chậm vì CPU | Dưới 20 FPS, một nhân CPU từ 90% trở lên | DXVK chạy thẳng trên chip ARM (bản arm64ec), giả lập CPU nhanh hơn, tắt Tiết kiệm pin |
| Game chậm, máy không cho biết GPU | Dưới 20 FPS đều, CPU không bận | Hạ Đồ họa, thử Gmem, DXVK arm64ec |

Cách sửa nào không đổi được gì thì không hiện. Ví dụ: không có nút đổi driver khi máy chỉ có một driver dùng được,
không có nút hạ Đồ họa xuống mức game đã từ chối.

## App biết gì về từng game (`files/agvn/doctor/`)

- **Cấu hình đã chạy được:** cấu hình của lần chơi gần nhất mà game hiện hình và không crash, kéo dài ít nhất 1
  phút hoặc người chơi tự thoát. Nút "Dùng lại cấu hình đã chạy được" đặt lại đúng cấu hình này.
- **Cách sửa đã thử** từ lần chạy tốt gần nhất.
- **Độ phân giải game từ chối:** từ đó hộp Đồ họa ghi "⚠ Lần trước game này không chịu chạy ở độ phân giải này",
  và hộp game chậm không gợi ý hạ xuống mức đó.

"Game đã hiện hình" nghĩa là app thấy cửa sổ game (từ 1/4 màn hình trở lên) được vẽ 20 lần. Hộp lỗi nhỏ của game
không tính. Game tắt khi chưa hiện hình nghĩa là game không mở được, kể cả khi người chơi bấm thoát sau 10 giây mà
chỉ thấy một hộp lỗi.

## Nhật ký Wine luôn có dòng lỗi

Khi tắt "Bật debug Wine", app vẫn cho Wine in hai nhóm lỗi: `err+module` (thiếu hoặc hỏng file `.dll`) và
`err+mscoree` (.NET). Hai nhóm này chỉ in khi có lỗi thật, nên không làm game chậm.

## Thêm một lỗi mới

1. Lấy câu lỗi thật trong `wine-cuoi.txt` hoặc nhật ký engine của người chơi gửi về.
2. Thêm một mục vào `game-problems.json`: `id`, `lines` (biểu thức chính quy), `when`, `title`, `cause` (viết cho
   người chơi), `fixes` (chọn trong các cách sửa đã có).
3. Thêm một ca vào `AgvnDoctorTest` với đúng câu lỗi đó.
4. Nâng `AGVN_VERSION_CODE` rồi phát hành.

Cách sửa mới (một nút mới) thì cần thêm code ở `AgvnFixes` và `AgvnFixApply`.

## Chưa làm

- Game "Chạy nhẹ" (Ren'Py, RPG Maker, HTML) vẫn dùng hộp lỗi riêng đã có: thiếu RTP, "Chạy bằng Windows". Chưa có
  hộp game chậm.
- Khi đang bật chế độ Big Picture, app chờ hỏi xong mới mở Big Picture. Riêng lỗi "Android đã tắt app" được tìm ra
  sau khi Big Picture đã mở, nên hộp hỏi nằm bên dưới Big Picture.

## Thử trên máy

- [ ] Đặt Đồ họa Siêu nhẹ cho game báo "The current resolution is too low", mở game, bấm OK. Về thư viện thì có hộp
      "Game không chạy ở độ phân giải 640×360" kèm nút "Đổi Đồ họa sang Thấp (854×480)". Bấm nút, game mở lại và chạy.
      Mở hộp Đồ họa, kéo về Siêu nhẹ: có dòng ⚠.
- [ ] Đổi DXVK của một game đang chạy tốt sang bản game không chạy được. Hộp hỏi có "Dùng lại cấu hình đã chạy được".
- [ ] Game nặng trên máy yếu (GPU 100%): sau khoảng 2,5 phút, thanh "Game đang chạy chậm" hiện với nút hạ Đồ họa và
      nút Gmem. Bấm "Để vậy, không hỏi lại": lần sau không hiện.
- [ ] Game Godot báo "Unable to initialize video driver": hộp hỏi có "Cho game Godot chạy bằng Vulkan".
- [ ] Bật Tiết kiệm pin rồi mở game: có hộp hỏi trước khi vào game. "Chơi luôn" vào game, không có thêm thông báo.
- [ ] Chơi bình thường rồi thoát: không có hộp nào.
- [ ] `adb logcat -s AGVN`: dòng `doctor: <lỗi> for <game>` và `fix <cách sửa> -> <giá trị>`.
