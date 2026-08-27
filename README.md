# Thời gian biểu (PWA)

Thời gian biểu cá nhân dạng PWA, cài được lên điện thoại. Module đầu tiên là **Giấc ngủ**:
duy trì thói quen lên giường sớm bằng cơ chế thưởng/phạt tiền, chuỗi kỷ luật và checklist
chuẩn bị ngủ. Dữ liệu lưu trên máy và đồng bộ lên Google Sheets.

Cấu trúc tách sẵn theo module (`js/rules.js` giữ luật, `js/app.js` giữ giao diện) để sau này
thêm các mảng khác của thời gian biểu mà không phải viết lại phần đồng bộ.

## Cấu trúc

| Đường dẫn | Vai trò |
|---|---|
| `index.html` | Toàn bộ giao diện, 4 tab |
| `css/style.css` | Giao diện tối, tối ưu cho điện thoại |
| `js/rules.js` | Luật thưởng/phạt, tính chuỗi, định dạng tiền |
| `js/sync.js` | Gọi Google Apps Script (chống lỗi CORS) |
| `js/app.js` | Lưu trữ, render, hàng đợi đồng bộ, nhắc nhở |
| `sw.js` | Service worker: chạy offline |
| `manifest.webmanifest` | Cài lên màn hình chính + lối tắt 1 chạm |
| `gas/Code.gs` | Mã dán vào Google Apps Script |

## 1. Cài phía Google Sheets

1. Mở Google Sheet → **Tiện ích mở rộng → Apps Script**.
2. Xóa mã cũ, dán toàn bộ `gas/Code.gs`, bấm Lưu.
3. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone** ← bắt buộc, chọn sai sẽ bị lỗi 403.
4. Copy **Web app URL** (kết thúc `/exec`) → dán vào tab **Cài đặt** của app → **Lưu URL** → **Kiểm tra**.
5. Bật tự động: trong Apps Script chọn hàm **`setupTriggers`** rồi bấm **Run** (cấp quyền một lần).

> Mỗi lần sửa mã trong Apps Script phải **Deploy → Manage deployments → Edit → New version**,
> nếu không URL cũ vẫn chạy mã cũ.

## 2. Chạy app

Chạy thử trên máy tính:

```bash
python -m http.server 8080
# mở http://127.0.0.1:8080
```

Để cài lên điện thoại, cần **HTTPS** (service worker không chạy trên HTTP LAN).
Cách nhanh nhất là đưa thư mục này lên một trong các dịch vụ miễn phí:

- **GitHub Pages** — push repo, bật Pages, dùng link `https://<user>.github.io/<repo>/`
- **Netlify Drop** — kéo thả thư mục vào <https://app.netlify.com/drop>
- **Cloudflare Pages** — kết nối repo, không cần build

Mở link trên Chrome/Safari điện thoại → menu → **Thêm vào màn hình chính**.

## 3. Tự động hóa đang có

| Việc | Chạy ở đâu | Khi nào |
|---|---|---|
| Nhắc chuẩn bị ngủ (email) | Google (cloud) | Mỗi tối, giờ `CONFIG.REMIND_HOUR` |
| Quên ghi → tự phạt + reset chuỗi | Google (cloud) | Sáng hôm sau, giờ `CONFIG.MISS_CHECK_HOUR` |
| Báo cáo 7 ngày (email) | Google (cloud) | Tối Chủ nhật |
| Thông báo đẩy trong máy | Điện thoại | Giờ nhắc + giờ mục tiêu |
| Gửi lại dữ liệu khi có mạng | Điện thoại | Tự động, có hàng đợi |
| Lối tắt "Ghi ngay" | Điện thoại | Giữ icon app trên màn hình chính |

## 4. Điều chỉnh luật

Toàn bộ trong tab **Cài đặt**: các mốc giờ, số tiền, mốc thưởng chuỗi, các bước chuẩn bị ngủ.
Sửa xong bấm **Lưu cài đặt** — lịch sử cũ sẽ được tính lại theo luật mới và tự đồng bộ lại lên Sheet.
Mốc rạng sáng gõ dạng **24:00 – 27:00** (tức 0h – 3h).
