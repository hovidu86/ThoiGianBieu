# Thời gian biểu

Thời gian biểu cá nhân, giữ kỷ luật bằng tiền và bằng khoá máy.

Ba mặt của **cùng một ứng dụng**, dùng chung một Google Sheet:

| Nơi | Có gì | Dùng khi |
|---|---|---|
| [`android/`](android/) | **Bản chính.** Giấc ngủ + khoá máy theo giờ + dùng ngắt quãng | hằng ngày, trên điện thoại |
| [`desktop/`](desktop/) | Khoá máy tính theo giờ | tối, khi ngồi máy tính |
| gốc repo | Bản web (PWA) của module Giấc ngủ | xem trên máy tính, hoặc khi chưa cài APK |

Bản Android là bản chính vì nó chạy nền thật: nhắc nhở đúng giờ và đồng bộ lên
Sheet kể cả khi không mở trình duyệt, việc mà PWA không làm được. Bản web giữ lại
để xem trên máy tính, cùng đọc ghi một Google Sheet nên số liệu luôn khớp.

## Ba module

**Giấc ngủ** — ghi giờ lên giường, ăn thưởng hoặc chịu phạt theo mốc giờ, giữ
chuỗi ngày kỷ luật, làm đủ checklist chuẩn bị ngủ thì được thưởng thêm. Toàn bộ
lịch sử đồng bộ lên Google Sheets.

**Khoá máy** — tới giờ đã hẹn thì tắt màn hình, mở lại phải nhập mã dài hai lần
cách nhau 15 giây, sau đó cứ 15 phút lại khoá tiếp. Có cả trên Android và Windows.

**Dùng ngắt quãng** (Android) — chỉ tính thời gian màn hình bật; dùng đủ hạn mức
thì bắt nghỉ. Chống việc trôi cả tiếng trong TikTok hay Shorts.

## Bắt đầu

### 1. Google Sheets (làm một lần)

1. Mở Google Sheet → **Tiện ích mở rộng → Apps Script**.
2. Xoá mã cũ, dán toàn bộ [`gas/Code.gs`](gas/Code.gs), bấm Lưu.
3. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone** ← bắt buộc, chọn sai là lỗi 403.
4. Copy **Web app URL** (kết thúc `/exec`).
5. Trong Apps Script chọn hàm **`setupTriggers`** rồi bấm **Run** để bật nhắc nhở
   qua email và báo cáo tuần.

> Mỗi lần sửa mã Apps Script phải **Deploy → Manage deployments → Edit → New
> version**, nếu không URL cũ vẫn chạy mã cũ.

### 2. Điện thoại

Cài [`android/dist-apk/ThoiGianBieu.apk`](android/dist-apk/ThoiGianBieu.apk),
mở app, vào tab **Cài đặt** dán URL Web App rồi bấm **Kiểm tra**.

Muốn dùng khoá máy và dùng ngắt quãng thì sang tab **Khoá máy**, bật ba quyền và
đặt mã. Xem [`android/README.md`](android/README.md).

### 3. Máy tính

Nhấn đúp [`desktop/Cai-dat.cmd`](desktop/Cai-dat.cmd), bấm **Yes** ở hộp thoại
quyền quản trị, rồi đặt mã mở khoá. Xem [`desktop/README.md`](desktop/README.md).

## Luật thưởng phạt

Sửa được hết trong app. Mặc định:

| Lên giường trước | Kết quả |
|---|---|
| 22:00 | +50.000 ₫ |
| 22:30 | +30.000 ₫ · vẫn giữ chuỗi |
| 23:00 | 0 ₫ |
| 23:30 | −20.000 ₫ |
| 24:00 | −50.000 ₫ · mất chuỗi |
| 25:00 | −100.000 ₫ |
| 26:00 | −150.000 ₫ |
| 27:00 | −250.000 ₫ |
| muộn hơn | −250.000 ₫ và thêm −100.000 ₫ mỗi giờ trôi qua |

Chuỗi ngày liên tiếp ngủ trước 22:30 được thưởng thêm ở mốc 3 / 7 / 14 / 30 ngày.
Làm đủ 5 bước chuẩn bị ngủ được thêm 20.000 ₫.

## Cấu trúc

```
android/     app gốc: giấc ngủ + khoá máy + ngắt quãng (Java thuần, 89 KB)
desktop/     khoá máy tính (PowerShell + WPF, không cần biên dịch)
gas/         Code.gs dán vào Google Apps Script
index.html   bản web của module Giấc ngủ
css/ js/     giao diện và luật của bản web
sw.js        service worker, chạy offline
```

Luật giấc ngủ tồn tại hai bản: [`js/rules.js`](js/rules.js) cho web và
[`android/…/LuatGiacNgu.java`](android/app/src/main/java/com/thoigianbieu/kiemsoat/LuatGiacNgu.java)
cho Android. Hai bản được đối chiếu tự động: chạy cùng một bộ 90 trường hợp qua
cả hai và so từng dòng, phải khớp tuyệt đối. Sửa luật ở một bên thì phải sửa bên
kia rồi chạy lại phép đối chiếu đó.

## Bảo mật

URL Web App **không bao giờ** nằm trong mã nguồn. Repo này công khai, ai đọc được
cũng ghi được dữ liệu rác vào Sheet của bạn. URL chỉ được dán một lần trong app và
nằm trong máy bạn.

Mã mở khoá máy chỉ lưu dạng băm SHA-256, không lưu mã gốc, không có cửa hậu.
