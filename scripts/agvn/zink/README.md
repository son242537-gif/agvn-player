# Zink (OpenGL chạy trên Vulkan) cho AGVN Player

`build-zink.sh` dựng lại `app/src/main/assets/graphics_driver/opengl_zink.tzst` bằng **Mesa 25.1.9 bản phát hành
(release)**. Nó thay bản Mesa 24.3.0 lấy nguyên từ Ludashi.

## Vì sao phải dựng lại

Bản của Ludashi được dựng trên điện thoại (Termux) ở chế độ gỡ lỗi. File còn nguyên thông tin gỡ lỗi (84 MB) và các
chốt kiểm tra `assert` vẫn bật. Game nào gắn một khối uniform lớn hơn mức GPU cho phép sẽ làm Zink dừng cả game:
`zink_context.c:700 assertion "…range <= …maxUniformBufferRange" failed`. Mesa đã sửa lỗi này từ 25.0.1 (commit
`b04eaa8589` "zink: clamp UBO sizes instead of asserting", MR !33819): Zink kẹp kích thước lại thay vì dừng game.
GameHub dùng Mesa 25.1.4, nên cùng game đó chạy được trên GameHub.

## Nguồn và cách kiểm chứng (ai cũng làm lại được)

| Thành phần | Nguồn | Kiểm chứng |
|---|---|---|
| Mesa 25.1.9 | thẻ `mesa-25.1.9` (commit `4a433b82…`), lấy qua bản sao trên GitHub | Thẻ có chữ ký PGP của Eric Engestrom, người quản lý phát hành Mesa (khoá `57551DE1 5B968F63 41C248F6 8D8E31AF C32428A6`, có trong `docs/release-maintainers-keys.asc` của Mesa). Cùng một thẻ trên hai bản sao độc lập (chaotic-cx, AHMETBAYIR). Script so khớp SHA của thẻ và commit. |
| Header X11/xcb/drm/zstd | 22 gói `-dev` của Ubuntu 26.04 (resolute) | SHA-256 lấy từ danh mục gói. Mã băm của danh mục nằm trong `InRelease`, có chữ ký của Ubuntu Archive Automatic Signing Key (2018). Chỉ giải nén bằng `dpkg-deb -x`, không chạy mã nào trong gói. |
| Thư viện để liên kết | imagefs của app (`imagefs_sha256` trong `scripts/agvn/pins.txt`) | Zink liên kết đúng với những thư viện nó sẽ nạp trên máy. |
| Trình biên dịch | Android NDK 29.0.14206865 (`scripts/agvn/cloud-setup.sh`) | |

Mọi thứ đều ghim trong `sources.lock`. File tải về sai một byte là script dừng ngay.

## Bản vá (`patches/`, áp theo thứ tự, không cho phép xê dịch dòng)

1. `0001`, `0002`: của Termux (commit `c12cdeaa03dc`). Tắt nhánh mã "Android" của Mesa, vì imagefs là môi trường
   kiểu Linux + X11.
2. `0003`, `0004`: của Pipetto-crypto (dùng trong Winlator và Ludashi). Sửa shader trên Mali và dùng conditional
   rendering chỉ khi driver có hỗ trợ. `0003` được AGVN chuyển sang 25.1.9, nội dung giữ nguyên.
3. `0005`, `0006`: Ludashi chưa công bố mã nguồn của hai sửa đổi này. AGVN dựng lại chúng từ mã máy của bản Ludashi và
   đã **đối chiếu từng lệnh**:
   - `0005`: tắt geometry shader trên Mali. Bản Ludashi ghi 0 vào vị trí `zink_screen+0xed0`, tức
     `info.feats.features.geometryShader` theo DWARF, khi driver là ARM (mã 9).
   - `0006`: đọc biến `WRAPPER_SURFACE_FORMAT`. Khi giá trị là `bgra8` thì bỏ 4 định dạng RGBA8888/RGBX8888, khi là
     giá trị khác thì bỏ 4 định dạng BGRA/BGRX. Nhờ vậy màu trùng với swapchain của bộ wrapper Vulkan và không bị
     đảo kênh.

Nếu tác giả Ludashi (StevenMXZ) công bố bản vá gốc (commit `9ffbe0175b`), nên so lại với `0005`/`0006`. Cách
dựng lại từ mã máy không nhìn thấy những sửa đổi giữ nguyên số dòng.

## Cách chạy (Linux x86_64: máy cloud hoặc WSL)

```bash
apt-get install -y meson ninja-build pkgconf patch bison flex zstd binutils python3-mako python3-yaml python3-packaging
./gradlew :app:downloadImageFS          # imagefs đã ghim, script cần nó để liên kết
bash scripts/agvn/zink/build-zink.sh    # khoảng 5–10 phút, cần khoảng 2 GB đĩa
```

Trước khi thay file, script tự kiểm:
- không còn RUNPATH và thông tin gỡ lỗi;
- mọi thư viện mà Zink cần đều có trong imagefs;
- bản vá thứ tự màu (0006) đã được áp.

Khi đổi sang bản Zink khác, tăng `AgvnGlDriver.ZINK_REVISION` để container cũ tự cài lại, và xoá `libgallium`
của bản trước.
