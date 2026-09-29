# Làm nhẹ game cho máy yếu (bộ công cụ PC của AGVN)

Công cụ chạy trên máy tính (Windows/Linux, Python 3.8+). Không truy cập mạng, không tự tải chương trình lạ.
Mục tiêu: game chạy mượt trên điện thoại yếu nhờ giảm texture trên PC, rồi AGVN Player áp profile khi nhập game.

Kết quả đo trên máy thật (xem `docs/agvn/device-findings.md`): giảm độ phân giải màn hình chỉ bớt ~16% tải GPU,
"Max Device Memory" không tiết kiệm RAM. Đòn bẩy thật là **exe đúng + driver đúng + texture nhỏ hơn**.

## Các file

| File | Việc |
|------|------|
| `do-bo-nho-anh.py <thư mục>` | Đo bộ nhớ GPU của texture, xuất CSV + top 30 file nặng nhất + ước tính tiết kiệm |
| `tao-agvn-profile.py <thư mục>` | Tạo `agvn-profile.json` (tự tìm exe, kiểm tra đúng luật của app) |
| `tao-agvn-profile.py <thư mục> --check` | Kiểm tra profile có sẵn, in ra exe mà app sẽ chạy |
| `them-vao-kho-cau-hinh.py <thư mục>` | Đưa profile đã thử trên máy thật vào kho có sẵn trong app: người chơi tải game ở đâu cũng tự nhận cấu hình (thêm `--match <file>` khi exe là tên chung như Game.exe) |
| `do-tai.sh [mẫu] [giây]` | Đo RAM trống, PSS của app, nhiệt độ pin, trạng thái nhiệt, % GPU bận qua adb |
| `agvn_profile_lib.py` | Luật chung (giống hệt app: `GameExeResolver`, `AgvnProfileValidator`) |
| `texture_headers.py` | Đọc kích thước DDS/PNG/JPG/TGA/BMP chỉ từ phần đầu file |
| `tests/` | `python -m unittest discover -s tools/agvn/tests` |

Tùy chọn cài thêm (anh tự tải từ trang chính thức, script không tự tải):
- `pip install UnityPy` để đo texture bên trong file Unity.
- repak hoặc FModel để giải nén `.pak`/`.utoc` của Unreal.
- ImageMagick (`magick`) để thu nhỏ ảnh và đo PSNR.

## Quy trình cho mỗi game

1. **Đo bản gốc:** `python tools/agvn/do-bo-nho-anh.py "D:/AGVN/Game"` → xem top 30 và dòng "tiết kiệm".
2. **Làm nhẹ** theo công thức của engine bên dưới, trên một **bản sao** của thư mục game.
3. **Kiểm tra chất lượng** (QC) như mục bên dưới. Không đạt thì làm lại file đó.
4. **Đo lại** bản đã làm nhẹ; ghi số MB trước/sau vào ghi chú phát hành của game.
5. **Tạo profile:** `python tools/agvn/tao-agvn-profile.py "D:/AGVN/Game" --name "Tên game"`
   - Unreal: texture pool mặc định theo RAM máy mục tiêu (`--ram-mb`): ≥7000 → 1024, ≥5000 → 768, còn lại 512;
     máy yếu (`weakDevice`) 512. Đổi bằng `--pool` / `--weak-pool`.
   - FPS 30 / máy yếu 24, độ phân giải 1280x720 / máy yếu 854x480. Đổi bằng `--fps --weak-fps --screen --weak-screen`.
   - Simulated Touchscreen bật sẵn (tắt bằng `--no-touch`).
   - Bộ phím ảo app tự chọn theo engine: Ren'Py/KiriKiri/Tyrano/Siglus/NScripter → `vn`, RPG Maker/Wolf RPG → `rpg`,
     GameMaker/Godot → `2d`, còn lại → `pc`. Game 3D hoặc game chỉ dùng chuột: thêm `--controls action` / `--controls mouse`
     (ghi vào `"controls"` trong profile).
6. **Chép lên điện thoại:** `/sdcard/AGVN/<Game>/` (cả thư mục game lẫn `agvn-profile.json`).
7. Trong app: Thư viện → **+** → chọn game → **Nhập**. Chơi thử 5–15 phút và chạy `do-tai.sh`.

## Công thức theo engine

### Unreal Engine 4/5
- Luôn chạy `<Game>/Binaries/Win64/<Game>-Win64-Shipping.exe` (script và app tự chọn). Bản Debug/launcher cần
  .NET/VC++ redist → Wine báo lỗi `0xc0000005`.
- Giải nén `.pak` bằng repak (hoặc FModel với `.utoc/.ucas`), đo thư mục đã giải nén; `.ubulk/.uptnl` được tính
  là dữ liệu texture.
- Ưu tiên: atlas Spine/UI rất lớn (thu nhỏ còn 1/2 cạnh), texture hiệu ứng (giảm 1/2), texture 4K của nhân vật
  phụ/cảnh nền.
- Đóng gói lại bằng repak với nén zlib; giữ đúng đường dẫn mount và tên pak (hoặc đặt `_P.pak` để ghi đè).
- Texture pool: không cần sửa file game. Profile đặt `texturePool`, app ghi `r.Streaming.PoolSize` vào
  `AppData/Local/<Project>/Saved/Config/Windows/Engine.ini` trong Wine trước mỗi lần chạy.
- Thêm cvar khác qua `ueEngineIni` trong profile, ví dụ tắt motion blur:
  `"ueEngineIni": {"SystemSettings": {"r.MotionBlurQuality": "0"}}`.

### Unity
- Đo bằng UnityPy. Texture lớn nhất thường nằm trong `sharedassets*.assets` hoặc AssetBundle.
- Giảm chất lượng mặc định: sửa `QualitySettings` (masterTextureLimit = 1 nghĩa là 1/2 cạnh) bằng UnityPy hoặc
  UABEA, hoặc đặt tham số chạy trong profile: `"args": ["-screen-width", "1280", "-screen-height", "720"]`.
- Texture Crunch: thu nhỏ còn 1/2 rồi nén lại; sau khi sửa, header phải hợp lệ (nếu anh có script
  `va-header-crn.py` thì kết quả phải là "va 0").
- `boot.config`: có thể thêm `gfx-disable-mt-rendering=1` nếu game giật do đa luồng (thử từng game).

### GameMaker / Ren'Py / game 2D khác
- GameMaker: ảnh nằm trong `data.win` (texture page). Dùng UndertaleModTool để xuất/thu nhỏ/nhập lại
  texture page; bản YYC không sửa shader được.
- Ren'Py: ảnh rời trong `game/` hoặc `.rpa`; thu nhỏ ảnh nền 4K còn 1080p là đủ cho điện thoại.

## Kiểm tra chất lượng (QC), bắt buộc trước khi phát hành

1. **PSNR ≥ 35 dB** cho ảnh đã thu nhỏ: phóng ngược về kích thước gốc rồi so với ảnh gốc
   ```
   magick nho.png -resize 2048x2048! tam.png
   magick compare -metric PSNR goc.png tam.png null:
   ```
   Dưới 35 dB (ảnh chữ, UI nhỏ): giữ nguyên kích thước gốc cho file đó.
2. **Header hợp lệ:** chạy lại `do-bo-nho-anh.py` trên bản đã sửa, mọi file DDS/PNG phải được đọc (không bị bỏ
   qua); texture Crunch kiểm tra như mục Unity.
3. **Không thiếu file:** chơi thử trên PC hoặc điện thoại qua màn hình chính, menu và 1 cảnh nặng nhất; không có
  ô vuông tím/đen hay lỗi "missing".
4. **Profile hợp lệ:** `tao-agvn-profile.py <thư mục> --check` báo "Hợp lệ".

## Đo trên điện thoại sau khi nhập bản nhẹ

1. Cắm cáp, bật gỡ lỗi USB, mở game đến cảnh nặng nhất.
2. `bash tools/agvn/do-tai.sh 12 10 ketqua.csv` (12 mẫu, mỗi 10 giây; FPS đọc trên HUD).
3. So với bản gốc: RAM trống phải cao hơn, PSS app thấp hơn, nhiệt độ không tăng.
4. Nếu RAM trống thấp hơn ~1,5 GB lúc bắt đầu: giảm `texturePool`/`weakDevice.texturePool` trong profile,
   nhập lại game (Thư viện → + → cùng thư mục → Nhập sẽ ghi đè).

## Giới hạn hiện tại
- `do-bo-nho-anh.py` không tự đọc `.pak`/`.utoc` nén hay mã hóa; cần giải nén trước.
- PNG/JPG được tính như RGBA8 kèm mipmap (trường hợp xấu nhất); game có thể nén lại khi nạp.
- Chưa thử trên game thật trong môi trường cloud; sai số ±10% cần kiểm chứng với AV Director (~2,5 GB),
  Legend Cleaner (~5,7 GB), NTR Soccer (~35 GB).
