# Hiệu năng trên mọi dòng máy

AGVN mới được đo trên một máy (POCO F8 Pro, Adreno 830). Tài liệu này ghi những gì app tự chỉnh theo máy, cách
chỉnh cho máy khác khi có người thử, và việc còn lại xếp theo thứ tự nên làm.

## App tự chỉnh theo máy

### Mức máy (`assets/agvn/device-tiers.json`)

Mức máy quyết định thanh "Đồ họa" ở "Tự động" bắt đầu ở đâu: Mạnh là Cao, Trung bình là Trung bình, Yếu là Thấp.
Luật xét theo thứ tự: tên GPU (Vulkan của máy), rồi chip nếu tên GPU không khớp luật nào, rồi RAM giới hạn mức.

| GPU | Ví dụ chip | Mức |
|---|---|---|
| Adreno 725, 730, 732, 735, 740, 750, 825 trở lên | 8 Gen 1–3, 8s Gen 3, 7+ Gen 2–3, 8 Elite | Mạnh |
| Adreno 710, 720, 722, 810 | 6 Gen 1, 7s Gen 2, 7 Gen 3, 7s Gen 3, 6 Gen 4 | Trung bình |
| Adreno 70x, Adreno 1xx–6xx (cả 642L) | 680, 695, 778G, 7 Gen 1, 865, 888 | Yếu |
| Mali-G610/G615 từ 6 nhân (MC6) | Dimensity 8100–8350 | Trung bình |
| Mali-G7xx, Immortalis, Mali-G9xx | Dimensity 9000–9400, 8400, Tensor G2–G4 | Trung bình |
| Xclipse, Maleoon, PowerVR DXT-48/72 | Exynos 2200–2500/1480/1580, Kirin 9000S, Tensor G5 | Trung bình |
| Mali khác, PowerVR khác | Helio G85/G99, Dimensity 6xxx–7300, Exynos 1280/1380, Unisoc | Yếu |

RAM (tổng hệ thống báo, máy 8 GB báo khoảng 7,3 GB): dưới 5000 MB tối đa là Yếu, dưới 7000 MB tối đa là Trung bình.

Số Adreno không phải đời máy: 725–750 là GPU flagship các năm 2022–2024, còn 810 là GPU tầm trung. Bảng cũ (Adreno
7xx là Trung bình, 8xx là Mạnh) đưa 8 Gen 1–3 xuống Trung bình và 7s Gen 3 lên Mạnh; luật cũ còn bỏ sót tên có chữ
sau số (642L của 778G). Mali và Immortalis chưa lên Mạnh vì driver Mali chạy DXVK kém hơn Turnip; nâng khi có số đo.

### DXVK theo Vulkan của máy (`AgvnDxvkPick`)

Container đầu tiên chọn DXVK theo việc Vulkan chạy được gì, không chỉ theo hãng GPU:

- Có Turnip (Adreno 6xx trở lên): DXVK 2.3.1.
- Không có Turnip, Vulkan của máy dưới 1.3 (Adreno 5xx, PowerVR GE8xxx, Xclipse đời đầu): DXVK 1.10.3, vì DXVK 2.x
  cần Vulkan 1.3.
- Mali: 1.10.3 như bản gốc. Máy không cho biết bản Vulkan: 2.3.1 như trước.

Container đã có giữ nguyên cài đặt. Lúc mở game, nếu game đặt DXVK 2.x mà driver Vulkan của máy ("System") dưới
1.3, lần chạy đó dùng 1.10.3 (`AgvnDxvkPick.fitLaunch`). Nếu không, DXVK 2.x không tạo được DirectX và game báo
thiếu DirectX 11. Turnip và driver tự cài thì giữ nguyên lựa chọn.

### OpenGL cho game Godot (`AgvnGlDriver.forGodot`)

Game Godot 4 dùng "Compatibility" và Godot 3 dùng GLES3 đều đòi OpenGL 3.3. Nếu không có, game dừng với hộp
"Unable to initialize video driver … OpenGL 3.3". Trên Zink (máy không có Turnip), app đặt
`MESA_GL_VERSION_OVERRIDE=3.3` và `MESA_GLSL_VERSION_OVERRIDE=330` cho game Godot, trừ khi người chơi đã tự đặt.
Turnip dùng freedreno, vốn đã đặt 3.3.

### Game "Chạy nhẹ"

- **Màn hình 60 Hz** (`AgvnRefreshCap`): Ren'Py và game HTML vẽ theo mỗi nhịp màn hình. Màn 90/120/144 Hz làm chúng
  vẽ gấp 2–3 lần mà game không mượt hơn (game chỉ chuyển động 60 lần/giây), nên chỉ thêm nóng và tốn pin. RPG Maker MV
  đời đầu còn chạy nhanh gấp đôi. Khi các game này chạy, app xin màn hình chế độ 60 Hz cùng độ phân giải. RPG Maker
  XP/VX/VX Ace (mkxp-z) giữ nguyên, vì XP chạy 40 FPS và chỉ màn 120 Hz chia đều được. Bật "Tần số quét cao" trong
  Cài đặt thì app không đổi màn hình.
- **Bộ đệm ảnh Ren'Py**: "Chạy nhẹ" giờ cũng giới hạn như trong Wine (`AgvnMemorySaver.applyRenpyLight`). Siêu nhẹ là
  128 MB, Thấp 192 MB, Trung bình 256 MB; Cao và Rất cao để game tự chọn (xem `giam-ram.md`).

### Tiết kiệm pin (`AgvnPowerSave`)

Tiết kiệm pin hạ xung CPU trên hầu hết máy, game giả lập chậm thấy rõ. Nếu nó đang bật lúc mở game (Windows hay
"Chạy nhẹ"), app báo một lần. App không tự tắt nó.

### thiet-bi.txt: để chỉnh cho máy chưa đo

"Gửi nhật ký" của game (kể cả khi game đang chạy) có thêm `app/thiet-bi.txt`. File `thiet-bi.txt` của "Xuất nhật ký
lỗi" trong Cài đặt có thêm các dòng này:

- máy, chip, GPU, Vulkan của máy;
- xung tối đa từng cụm nhân CPU;
- RAM tổng, RAM trống, ngưỡng Android bắt đầu đóng app;
- các tần số quét của màn hình;
- nhiệt pin, trạng thái nhiệt, headroom;
- Tiết kiệm pin, chế độ game của Android, máy có ADPF hay không;
- driver đã thử được hay lỗi;
- mức máy và vì sao. Ví dụ: `GPU "Adreno (TM) 830" → FLAGSHIP; RAM 11000 MB → FLAGSHIP; lấy mức yếu hơn = FLAGSHIP`.

Tên GPU được nhớ trong `files/agvn-device.properties`. Nhờ vậy tiến trình riêng của Ren'Py và mkxp-z biết mức máy
mà không phải nạp driver Vulkan.

Mỗi phiên chơi game Windows còn có `wine-cuoi.txt`: 300 dòng cuối Wine và game in ra, kể cả khi tắt debug Wine
và game tự thoát. Trong đó có câu lỗi của game (ví dụ Godot báo "OpenGL 3.3"), dòng DXVK báo GPU và lỗi, dòng lỗi
của Mesa/Zink (`AgvnWineTail`).

## Khi người chơi máy khác báo game chậm

1. Xin họ "Gửi nhật ký" ngay lúc game đang chậm, rồi mở `app/thiet-bi.txt`.
2. Nếu "Tiết kiệm pin BẬT", nhờ họ tắt rồi thử lại.
3. Nếu mức máy sai (dòng "Mức máy" nói vì sao):
   - sửa luật trong `device-tiers.json`;
   - thêm đúng tên GPU hoặc chip đó vào `DeviceTierTest`;
   - nâng `AGVN_VERSION_CODE`, rồi phát hành.
4. Nếu "Driver đã thử" có lỗi, hoặc game đen màn hình trên Mali/PowerVR: xem `logcat.txt` và mục "Còn lại" bên dưới.

## Còn lại, theo thứ tự nên làm

1. **Nhiệt cho game "Chạy nhẹ":** chưa có cảnh báo RAM và nhiệt như game Windows. Nên thêm cảnh báo và hạ FPS khi
   máy nóng; mkxp-z có sẵn giới hạn FPS.
2. **Ưu tiên nhân CPU mạnh:** nhánh `agvn/p44-game-cpu` dùng ADPF và `nice -2`, đo trên POCO thấy có lợi. Nên gộp vào
   sau khi thử thêm một máy Mediatek, vì HyperOS bỏ việc ghim nhân.
3. **Máy RAM 3–4 GB:** mức "Siêu nhẹ" tự động cho máy dưới 4,5 GB RAM. Cần đo trước trên một máy 4 GB: chữ ở
   640×360 có thể khó đọc.
4. **Mali/PowerVR:**
   - BCn đang được giải nén hai lần (wrapper và DXVK) khi không có Turnip;
   - nên ẩn driver không dùng được;
   - nên nhớ lần chuyển driver để khỏi báo mỗi lần mở game;
   - nên cảnh báo khi chọn DXVK 2.x hoặc VKD3D.
   Mọi việc ở đây cần một máy Mali để thử.
5. **Chế độ game của Android:** khai báo `game_mode_config` để màn hình chế độ game của máy (Game Turbo, Game
   Booster…) chỉnh được mức FPS và độ phân giải cho AGVN.
6. **Mức Mạnh cho Mali/Immortalis đời mới** (Dimensity 9300/9400): chỉ nâng khi đã đo được 30 FPS ổn định.

## Thử trên máy

- [ ] POCO F8 Pro: `thiet-bi.txt` ghi `Mức máy: FLAGSHIP`, đúng GPU, xung CPU và tần số quét; game Windows vẫn như cũ.
- [ ] Ren'Py "Chạy nhẹ" trên màn 120 Hz: lệnh `adb shell dumpsys SurfaceFlinger | grep -i "refresh"` cho thấy 60 Hz
      khi đang chơi và trở lại bình thường khi thoát. Bật "Tần số quét cao" trong Cài đặt thì màn hình giữ 120 Hz.
- [ ] RPG Maker MV/MZ "Chạy nhẹ": 60 Hz khi chơi, nhạc và tốc độ game như cũ.
- [ ] RPG Maker XP "Chạy nhẹ": màn hình giữ nguyên tần số.
- [ ] Ren'Py "Chạy nhẹ", "Đồ họa" ở Thấp: có `game/zz_agvn_mem.rpy` (192 MB). Kéo lên Cao rồi mở lại game: file mất.
- [ ] Bật Tiết kiệm pin rồi mở một game Windows và một game "Chạy nhẹ": mỗi game báo một lần.
- [ ] "Gửi nhật ký" từ menu bên của game Ren'Py/RPG Maker "Chạy nhẹ" có `app/thiet-bi.txt` với tên GPU.
- [ ] Máy khác (nếu có người thử): gửi `thiet-bi.txt` để chỉnh `device-tiers.json`.
