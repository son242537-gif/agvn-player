# Giảm RAM khi hạ thanh "Đồ họa"

Điện thoại dùng chung RAM cho CPU và GPU. Vì vậy texture và bộ đệm đồ hoạ của game là RAM thật: trong
`dumpsys meminfo`, chúng nằm ở các dòng `GL mtrack` và `Gfx dev`. Không lớp dịch nào (DXVK, VKD3D, Zink) tự thu nhỏ
được texture mà game đã tạo. Muốn bớt RAM thì phải bảo engine của game dùng ít đi, hoặc bớt phần mà chính lớp dịch
giữ lại. Tài liệu này ghi lại AGVN làm gì ở từng mức và vì sao.

## AGVN làm gì ở từng mức

| Thành phần | Siêu nhẹ | Thấp | Trung bình | Cao, Rất cao |
|---|---|---|---|---|
| FPS lúc vào game (mọi loại game; chỉnh lại được trong game) | 20 | 24 | 30 | Cao: 30. Rất cao: không giới hạn |
| DXVK: khối bộ nhớ 16 MB (`dxvk.maxChunkSize=16`) | có | có | có | không |
| DXVK: giải phóng pipeline library không dùng (`dxvk.trackPipelineLifetime=True`) | có | có | không | không |
| Ren'Py: bộ đệm ảnh (`config.image_cache_size_mb`), trong Wine và "Chạy nhẹ" | 128 MB | 192 MB | 256 MB | để game tự chọn |
| Unity: mức chất lượng thấp nhất của game | có | có | không | không |
| Unreal: texture pool (`r.Streaming.PoolSize`); với driver của máy (Mali, Qualcomm) là một phần tư, ít nhất 64 MB | 384 MB | 512 MB | 768 MB | Cao: 1024 MB. Rất cao: 1536 MB |
| Zink (OpenGL): vùng đệm bộ đệm GPU đã dùng xong | 256 MB | 256 MB | 256 MB | 256 MB |

Mức "Tự động" dùng mức gợi ý cho máy: máy yếu là Thấp, máy tầm trung là Trung bình, máy flagship là Cao.

## Khi game từng hết RAM trên máy

Ở bất kỳ mức nào, app dùng phần tiết kiệm RAM của Siêu nhẹ cho một game đã từng bị tắt vì hết RAM trên máy này
(`AgvnMemorySaver.ranOut`). Độ phân giải và FPS vẫn theo mức đã chọn. "Tự sửa lỗi" gặp lỗi `memory`, `gpu-memory`,
`killed-low-memory` hoặc `low-ram-end` thì đánh dấu game (`agvnRamShort` trong lối tắt), và hộp hỏi báo "Từ lần mở sau,
app tự dùng mức tiết kiệm RAM cao nhất mà game này có".

Chỉ lỗi thật của chính game đó mới làm app giảm thêm, trên mọi máy. RAM hay GPU của máy thì không. Bản 0.1.17 còn giảm
như Siêu nhẹ cho mọi game DirectX trên máy dưới 9 GB RAM chạy driver của máy (Mali, và Qualcomm khi không dùng Turnip),
dù người chơi chọn mức nào. Kết quả là cả những game vẫn chạy tốt cũng bị giảm chất lượng (máy Mali-G615 chọn mức
FLAGSHIP vẫn bị giảm). Bản sau bỏ cách đó, theo nguyên tắc của người bảo trì (05/10): sửa lỗi phải áp dụng chung, không
hy sinh game khác để sửa lỗi riêng của một máy.

| Engine | Phần giảm |
|---|---|
| DXVK (mọi game DirectX 8–11: Unity, Unreal, game khác) | Khối bộ nhớ 16 MB, giải phóng pipeline library không dùng |
| Unity | Mức chất lượng thấp nhất của game |
| Unreal | Texture pool của Siêu nhẹ (384 MB), trên driver của máy là một phần tư: 96 MB |
| Ren'Py (Wine và "Chạy nhẹ") | Bộ đệm ảnh 128 MB, chỉ khi game từng hết RAM (ảnh Ren'Py không phải BCn) |
| Zink (OpenGL) | Như mọi mức |

Game chưa từng hết RAM thì giữ phần tiết kiệm của mức đã chọn. Trên driver của máy, texture pool của Unreal vẫn là một
phần tư (xem bảng trên), vì đó là cách tính đúng RAM chứ không phải giảm thêm. `su-kien.txt` của phiên chơi ghi "Tiết
kiệm RAM như Siêu nhẹ: game từng bị tắt vì hết RAM trên máy này" khi app giảm.

"Đồng bộ khung hình" và "Tắt Present Wait" là cách một người chơi Mali-G615 (7,2 GB) mở được Support Pregnancy School
(Unreal): với mặc định, game đen màn hình rồi không vào được. Bản 0.1.18 tự bật hai mục này cho mọi game DirectX trên
máy như vậy. Bản sau chỉ đề nghị chúng trên thanh "Màn hình game vẫn đen" của game DirectX, trên mọi máy (xem
`tu-sua-loi.md`).

App chưa giảm được texture của Godot, KiriKiri, RPG Maker (XP/VX/VX Ace, MV/MZ) và game HTML: chưa có thiết lập nào
app ghi được từ bên ngoài game cho phần này.

## Chi tiết

- **Giới hạn FPS:**
  - Thanh chỉ đặt mức bắt đầu của "Giới hạn FPS" trong game.
  - X server giữ nhịp khung hình ở tầng Present, nên áp được cho OpenGL, WineD3D, VKD3D và DXVK (`PresentExtension`).
  - Mặc định nhịp này là bộ hẹn giờ của bản gốc. Ô "Khớp nhịp màn hình (thử nghiệm)" ngay dưới thanh, lưu riêng cho
    từng game, cho nhịp bám vsync của màn hình (`AgvnVsyncLimiter`): mỗi khung đứng yên đúng N nhịp màn hình, game được
    trả buffer ngay lúc khung trước lên màn. Khi đó FPS thật chỉ là ước số của tần số màn: màn 60 Hz chạy 60, 30 hoặc
    20; màn 120 Hz thêm 40 và 24. Mức không chia đều được làm tròn về ước số gần nhất, không vượt quá mức đã chọn hơn
    10% (24 và 27 trên màn 60 Hz thành 20), và ô "Giới hạn FPS" hiện cả hai số, ví dụ "25 → 20 FPS".
  - Ô này còn thử nghiệm: ngày 01/10 một game đứng hình 5–9 giây, hai lần trong 30 giây, khi bật. Mỗi lần game ngừng gửi
    khung hình từ 1 giây trở lên, nhật ký của lần chơi (và logcat, thẻ `AGVN`) có một dòng "Đứng hình …" ghi giới hạn FPS
    đang chạy theo cách nào. Với khớp nhịp, dòng đó còn cho biết app có giữ bộ đệm nào của game không, tức đứng hình do
    app hay do game (`AgvnFrameStalls`).
  - Trong game, người chơi nâng, hạ hoặc tắt được. Khi tắt, game và menu cheat tự chỉnh FPS.
  - AGVN không còn ghi `DXVK_FRAME_RATE`: DXVK khoá cứng mức này, trong game không nâng lên được. Game cũ có biến này
    được chuyển sang "Giới hạn FPS" khi mở (`AgvnQuality.upgrade`).
  - Cách này cần bật "Dùng tiện ích DRI3".
- **DXVK 2.3.1:**
  - Theo mặc định, DXVK xin bộ nhớ theo khối 64 MB cho mỗi loại bộ nhớ của Turnip, và giữ lại một khối trống mỗi
    loại. Khối 16 MB bớt phần xin thừa này.
  - Theo mặc định, DXVK chỉ giải phóng pipeline library cho game 32-bit. Ở Siêu nhẹ và Thấp, nó làm vậy cho mọi game.
    Đổi lại, game có thể khựng nhẹ khi gặp lại shader cũ.
  - Nguồn: `dxvk.conf` và `src/dxvk/dxvk_memory.cpp` của bản v2.3.1.
- **Ren'Py:**
  - AGVN ghi file `game/zz_agvn_mem.rpy`, chạy ở `init 999`, tức sau code của game. Ở mức Cao, AGVN xoá file này cùng
    với `.rpyc` mà Ren'Py dịch ra.
  - Mặc định của Ren'Py là 300–400 MB, và một số game tự nâng lên rất cao.
  - File nằm trong thư mục game, nên cũng có tác dụng khi chạy game bằng app khác.
- **Unity:**
  - AGVN ghi giá trị `HKCU\Software\<công ty>\<tên game>\UnityGraphicsQuality_h1669003810 = 0` vào `user.reg` của
    container. Công ty và tên game lấy từ hai dòng đầu của `<exe>_Data/app.info`.
  - Ở mức cao hơn, AGVN xoá giá trị này, nhưng chỉ khi chính AGVN đã ghi nó.
  - Mức thấp nhất của Unity thường giảm độ phân giải texture và tắt bóng. Game có menu cài đặt riêng có thể ghi đè.
- **Unreal trên driver của máy:**
  - Unreal tính texture theo cỡ nén BCn. Driver của máy (Mali, và Qualcomm khi không dùng Turnip) không đọc được BCn,
    nên wrapper giải nén texture ra lớn gấp 4–8 lần. Texture pool vì vậy chiếm RAM gấp ít nhất 4 lần con số đặt.
  - AGVN đặt pool bằng một phần tư mức của thanh, ít nhất 64 MB và không lớn hơn mức gốc (`AgvnBcn.texturePool`).
    Turnip đọc BCn trực tiếp nên giữ nguyên mức.
  - Ví dụ ngày 05/10: Legend Cleaner (Unreal) trên máy Mali-G610 7,2 GB lên 4,1 GB khi đang tải rồi bị tắt vì hết RAM.
  - Đổi lại, texture của game Unreal trên các máy này mờ hơn. Mức mới chưa được đo trên máy thật.
- **Zink:** bản vá `scripts/agvn/zink/patches/0007-agvn-cap-buffer-cache.patch`. Trên POCO F8 Pro, bộ nhớ GPU của một
  game OpenGL giảm từ 2,78 GB xuống 2,52 GB.

## Khi game vẫn bị tắt vì hết RAM

Thanh "Đồ họa" chỉ bớt được những phần trong bảng trên. Nếu game cần nhiều RAM hơn máy còn trống, hệ thống tắt tiến
trình game, thường không để lại dòng lỗi nào. App nhận ra trường hợp này và hỏi "Game tự tắt lúc máy gần hết RAM"
(`tu-sua-loi.md`). Khi RAM trống dưới hai lần mức cảnh báo, app đọc RAM mỗi giây thay vì 5 giây, để không bỏ sót game
dùng hết RAM giữa hai lần đo (`AgvnMemoryWatch`).

Ví dụ ngày 04/10: With The Devilish Her (Unity 2021.3, bản Việt hoá GameHub) trên Xiaomi 23090RA98G (Mali-G610, RAM
7,2 GB, còn trống 3,0 GB trước khi chơi). App và game lên 4,0–4,2 GB RSS, RAM trống xuống 0,4–0,7 GB. Cả bốn lần chơi,
game đều bị tắt ở cùng một cảnh, khoảng 2,5 phút sau khi mở.

- **Người chơi:**
  - Đóng các app khác hoặc khởi động lại máy trước khi chơi.
  - Lưu game trước đoạn hay bị tắt.
  - Khi thanh "Game đang dùng quá nhiều RAM" hiện, lưu game rồi thoát.
- **Người làm bản vá game Unity:**
  - GPU Mali không đọc được texture BCn (DXT1, DXT5, BC7), nên wrapper giải nén chúng ra RGBA8, lớn gấp 4–8 lần.
    Driver Qualcomm cũng vậy. Turnip thì đọc được BCn, nên game vừa RAM trên POCO F8 Pro vẫn có thể hết RAM trên máy
    Mali có cùng dung lượng RAM.
  - Trong game, GPU Mali có `SystemInfo.graphicsDeviceVendor` là `ARM` (Player.log ghi `Vendor: ARM`). RAM của máy là
    `SystemInfo.systemMemorySize`.
  - `QualitySettings.masterTextureLimit` chỉ thu nhỏ texture có mipmap. Ảnh CG và sprite thường không có mipmap nên vẫn
    được nạp đủ cỡ.
  - Chỉ nạp thứ gì khi cần. Bản vá ở ví dụ trên nạp sẵn 3 gói DLC lúc mở game, và Player.log có hai lần dòng
    `Warmed 38 gallery thumbnail(s)`.
  - `tools/agvn/do-bo-nho-anh.py` đo bộ nhớ texture của game trên PC. Số đo là cỡ texture khi GPU đọc được BCn, chưa
    tính phần lớn thêm trên Mali.

## Những cách không giúp hoặc chưa làm

- **Giới hạn VRAM báo cho game:** `WRAPPER_VMEM_MAX_SIZE`, `dxgi.maxDeviceMemory`, `VideoMemorySize` của WineD3D.
  Cách này chỉ giúp những game tự chọn chất lượng theo VRAM. Lần đo với game Unreal DX11 trong `device-findings.md`
  không bớt được RAM. Với WineD3D, d3d8/d3d9 còn có thể báo lỗi hết bộ nhớ.
- **Một số tuỳ chọn DXVK:**
  - `dxvk.maxMemoryBudget` chỉ dùng để gỡ lỗi.
  - `d3d9.textureMemory` không đổi lượng bộ nhớ dùng thật.
  - `samplerLodBias` vẫn giữ nguyên texture đầy đủ trong bộ nhớ.
- **VKD3D-Proton, Mesa, Turnip:** không có biến môi trường nào giới hạn bộ nhớ.
- **Chưa làm, cần máy thật để đo:**
  - Wrapper của GameNative đổi BCn sang ASTC thay vì giải nén ra RGBA8 (lớn gấp 4–8 lần). Cách này chỉ có ích với
    driver không hỗ trợ BCn, như driver Qualcomm và Mali. Turnip hỗ trợ BCn sẵn.
  - Trên Mali, bộ giải BCn bằng compute và bộ giải BCn của wrapper đang cùng bật (`ENABLE_BCN_COMPUTE` và
    `WRAPPER_EMULATE_BCN=3`).
  - Cờ V8 `--optimize-for-size` cho game NW.js (RPG Maker MV/MZ, Tyrano).
  - Giảm số luồng dịch shader của DXVK.
