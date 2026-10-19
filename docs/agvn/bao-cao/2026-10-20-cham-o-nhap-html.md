# AGVN Player — Game RPG Maker MV/MZ treo ở màn nhập liệu HTML (nhập tên, nhập số…) trên điện thoại

Ngày: 20/10/2026 · Máy thử: Android 16 / HyperOS, WebView Chromium · Chế độ: chạy HTML (WebView)

## 1. Hiện tượng

- Game có màn nhập liệu dựng bằng thẻ HTML (`<input>`, `<textarea>`, `<select>`, nút `<input type="submit">`) đặt đè lên khung game.
  Ví dụ hay gặp: đặt tên nhân vật chính lúc vào ván mới, nhập số lượng, nhập hồ sơ.
- Lúc mới hiện, ô nhập tự focus nên bàn phím có lên và gõ được. Nhưng khi focus mất (bấm ra ngoài, ẩn bàn phím), **chạm vào ô
  nhập hoặc nút "Xác nhận/Hủy" không có tác dụng gì nữa**. Game đứng chờ mãi: không lỗi, không văng, nhạc vẫn chạy.
- Trên PC (chuột) cùng game chạy bình thường.
- Nhật ký (logcat) chỉ thấy các sự kiện chạm `ACTION_DOWN/UP` lặp liên tục. Console JS **không có lỗi nào**, vì trang không hỏng mà chỉ là chạm bị nuốt.

## 2. Nguyên nhân (đã xác nhận trên máy thật qua cổng gỡ lỗi WebView)

1. **Lõi engine chặn chạm.** `TouchInput._onTouchStart` của RPG Maker MV (`rpg_core.js`, MZ tương tự trong `rmmz_core.js`)
   gắn trên **toàn bộ document**. Hễ điểm chạm nằm trong khung game là gọi `event.preventDefault()`. Trên màn cảm ứng, chặn
   `touchstart` thì trình duyệt **không phát `click` và không chuyển focus** cho phần tử HTML. Ô nhập và nút đè lên khung game
   nên luôn nằm "trong khung game", và không bao giờ nhận được chạm.
2. **Kích thước cố định theo px.** Plugin nhập liệu thường đặt vị trí theo tỉ lệ khung game (`Graphics._realScale`), nhưng bề
   ngang/cao/lề của ô lại cố định bằng px trong CSS. Trên điện thoại khung game bị thu nhỏ mạnh (đo được tỉ lệ ~0,48–0,54),
   ô nhập vẫn giữ nguyên to → **đè lên nút Xác nhận bên cạnh**. Nút thì còn ~44×14 px, rất khó chạm trúng.

Trạng thái đọc được lúc treo (`document.activeElement`, chế độ chờ của sự kiện, toạ độ phần tử): ô có chữ đã gõ, focus đã
về `BODY`, sự kiện đang ở chế độ chờ nhập, nút Xác nhận nằm chồng lên ô nhập.

## 3. Đề xuất sửa trong app (áp cho mọi game RPG Maker MV/MZ ở chế độ HTML)

Chèn một đoạn JS chạy **sau khi rpg_core/rmmz_core nạp, trước `SceneManager.run`** (engine gắn sự kiện chạm bằng
`this._onTouchStart.bind(this)` lúc khởi động, nên phải ghi đè trước thời điểm đó):

```js
(function () {
  var FORM = /^(INPUT|TEXTAREA|SELECT|OPTION|BUTTON|LABEL)$/;
  var goc = TouchInput._onTouchStart;
  TouchInput._onTouchStart = function (event) {
    var t = event.target;
    if (t && t.tagName && (FORM.test(t.tagName) || t.isContentEditable)) return; // để trình duyệt xử lý click/focus
    goc.call(this, event);
  };
})();
```

- Chỉ bỏ qua chạm **rơi đúng vào phần tử nhập liệu**. Chạm vào khung game vẫn như cũ, nên không ảnh hưởng điều khiển game.
- Tuỳ chọn thêm, cho đỡ đè: phần tử nhập liệu có bề ngang/cao cố định theo px thì co theo `Graphics._realScale` (đặt
  `box-sizing: border-box`, nhân cả lề/viền). Nút `submit` để nguyên vì cỡ chữ của nó đã co theo khung.
- Nếu app có lớp điều khiển ảo / bắt chạm riêng phủ lên WebView, cũng phải cho chạm đi qua tới phần tử nhập liệu.

## 4. Cách kiểm sau khi sửa

- Mở game có màn nhập tên → để bàn phím ẩn đi → chạm lại vào ô nhập: bàn phím phải lên lại. Chạm "Xác nhận": game phải đi tiếp.
- Kiểm bằng cổng gỡ lỗi: tạo `TouchEvent('touchstart')` có `target` là ô nhập, `dispatchEvent`, rồi xem `event.defaultPrevented`
  phải là `false` (trước khi sửa là `true`). So vị trí ô nhập và nút bằng `getBoundingClientRect()`: không được chồng nhau.

## 5. Phát hiện phụ (cùng lần đọc log)

- Một số game chọn ngôn ngữ theo **tên tệp exe** bằng `require('path').parse(process.execPath)`. Trong app `path.parse` không có
  (`path.parse is not a function`), game rơi vào nhánh mặc định. Lần này nhánh mặc định đúng là bản Việt nên không sao, nhưng với game khác có thể ra sai ngôn ngữ.
  Đề xuất: shim `require('path')` của app cần có đủ `parse/basename/extname/dirname/join`, và `process.execPath` trả về đường dẫn exe
  game thật (`.../Game.exe`).
