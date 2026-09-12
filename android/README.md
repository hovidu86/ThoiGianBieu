# Thời gian biểu — Android

Bản chính của *Thời gian biểu*. Ba tính năng:

0. **Giấc ngủ** — chuyển nguyên từ bản PWA sang: ghi giờ lên giường, thưởng phạt
   theo mốc giờ, chuỗi kỷ luật, checklist chuẩn bị ngủ, biểu đồ 14 đêm, đồng bộ
   Google Sheets. Chạy gốc nên nhắc nhở và đồng bộ không cần mở trình duyệt.
1. **Khoá theo giờ** — giống hệt bản Windows: tới giờ đã hẹn thì tắt màn hình,
   muốn dùng lại phải nhập mã dài **hai lần, cách nhau 15 giây**, sau đó cứ mỗi
   15 phút lại khoá tiếp cho tới giờ kết thúc. Gác giờ đi ngủ.
2. **Dùng ngắt quãng** — chia thời gian dùng máy thành từng đợt rời rạc, hết hạn
   mức thì bắt nghỉ. Chống việc trôi cả tiếng trong TikTok hay Shorts.

**Vì sao không phải PWA:** trình duyệt không có API nào tắt được màn hình hay khoá
thiết bị. Web chỉ có `WakeLock` để *giữ* màn hình sáng — đúng chiều ngược lại.
Bắt buộc phải là app gốc, dùng quyền quản trị thiết bị (`DevicePolicyManager.lockNow`).

## Cài lên điện thoại

APK dựng sẵn nằm ở `dist-apk/ThoiGianBieu.apk` (114 KB).

1. Chép file APK sang điện thoại — qua cáp, Zalo, Google Drive, hoặc tải thẳng từ
   GitHub bằng trình duyệt trên máy.
2. Mở file → Android hỏi *"Cài ứng dụng không rõ nguồn gốc"* → cho phép nguồn đó.
3. Mở app **Kiểm soát máy**.

Cần dựng lại APK sau khi sửa mã: nhấn đúp `Dung-apk.cmd`. File này dựng APK,
chép vào `dist-apk/`, và sinh lại `version.json` theo đúng `build.gradle`.

## Tự cập nhật

Từ bản 2.3, app tự dòm bản mới (nhiều nhất 6 tiếng một lần) bằng cách đọc
`android/version.json` trên GitHub. Có bản mới thì tab **Cài đặt** hiện nút
*Tải và cài bản x.y*: app tải APK về rồi mở thẳng trình cài đặt.

Android **không cho** ứng dụng tự cài đè chính nó mà không hỏi — trừ khi máy đã
root hoặc app là chủ sở hữu thiết bị. Nên đây là mức gần nhất có thể: một chạm,
không phải chép tay APK qua điện thoại nữa.

Lần đầu, Android sẽ hỏi cho phép app này cài ứng dụng — bật một lần rồi thôi.

Quy trình phát hành: tăng `versionCode` trong `app/build.gradle`, sửa `ghiChu`
trong `version.json`, chạy `Dung-apk.cmd`, rồi commit và push cả APK lẫn
`version.json`.

## Ba quyền bắt buộc

Màn hình chính có ba nút, bấm lần lượt từ trên xuống. Thiếu bất kỳ quyền nào thì
app không làm đủ việc:

| Quyền | Để làm gì | Thiếu thì sao |
|---|---|---|
| **Quản trị thiết bị** | gọi `lockNow()` tắt màn hình | không tắt được màn hình, chỉ còn thông báo suông |
| **Hiển thị trên ứng dụng khác** | dựng lớp phủ đòi mã | màn hình vẫn tắt nhưng không đòi mã, mở khoá điện thoại là dùng được luôn |
| **Bỏ tối ưu hoá pin** | báo thức nổ đúng giờ | Android hoãn báo thức tuỳ hứng, có đêm khoá trễ hoặc không khoá |

Quyền quản trị thiết bị chỉ xin đúng một chính sách `force-lock` (xem
`res/xml/quan_tri.xml`) — không xoá dữ liệu, không đổi mật khẩu máy.

Sau khi bật đủ ba quyền, đặt mã mở khoá (tối thiểu 10 ký tự, nhập hai lần) rồi bấm
**Lưu cài đặt**. Bấm **Khoá thử ngay bây giờ** để xem trước.

## Diễn biến một đêm

Với cấu hình mặc định 22:45 → 05:30:

- **22:30 / 22:40 / 22:44** — thông báo kèm rung, và một dải cảnh báo hiện đè lên
  màn hình đang dùng.
- **22:45** — màn hình tắt.
- Bật máy lên, mở khoá điện thoại như thường → lớp phủ đen đòi mã đã nằm sẵn ở đó
  → nhập mã → chờ 15 giây → nhập lại → mở.
- Chưa nhập đúng mà cứ để đó thì **cứ 2 phút màn hình lại tắt tiếp**.
- **23:00** — khoá lại. Cứ thế mỗi 15 phút.
- **05:30** — thôi khoá cho tới 22:45 tối hôm sau.

Khởi động lại điện thoại không mất gì: `KhoiDongReceiver` bắt `BOOT_COMPLETED`,
dựng lại toàn bộ lịch, và nếu lúc bật máy đang nằm trong khoảng khoá thì khoá lại
sau 1 phút. Ngoài ra có **nhịp tự hồi phục mỗi 30 phút** — nếu hệ thống dọn mất
báo thức hoặc máy tắt ngang qua mốc khoá, nhịp này khoá bù và dựng lại lịch.

Nhập sai ở lần 2 phải làm lại từ đầu, kể cả 15 giây chờ.

## Dùng ngắt quãng

Tính năng thứ hai, cho ban ngày. Mặc định **tắt**, bật trong Cài đặt.

Vấn đề nó giải: mở TikTok hay Shorts rồi trôi mất cả tiếng đồng hồ.

Cách hoạt động: **chỉ tính thời gian màn hình đang bật và máy đã mở khoá** — tắt
màn hình nghe nhạc, nghe podcast thì không tính một giây nào. Dùng đủ hạn mức
(mặc định 15 phút) thì màn hình tắt và hiện đồng hồ đếm ngược quãng nghỉ bắt
buộc (mặc định 5 phút). Hết giờ, màn hình đó tự tan.

Thời gian **cộng dồn qua nhiều lần mở màn hình rời rạc**: mở 5 phút, tắt, mở 4
phút, tắt, mở 6 phút — là đủ 15. Nhưng nếu khoảng nghỉ giữa hai lần mở **dài hơn
ngưỡng reset** (mặc định 5 phút) thì bộ đếm về 0. Nghỉ tử tế thì được dùng tiếp
trọn vẹn một đợt mới.

Tắt màn hình **chưa đủ** ngưỡng reset thì sao? Có ba cách tính, chọn trong tab
Khoá máy (từ v3.5, vì tình huống thật: còn 10 giây, tắt máy 4 phút, mở lại dùng
nốt 10 giây rồi phải nghỉ tiếp 5 phút — 9 phút chờ cho một quãng nghỉ 5 phút):

| Cách tính | Tắt 4 phút khi còn 10 giây | Ghi chú |
|---|---|---|
| Không tính gì (như cũ) | vẫn còn 10 giây, rồi nghỉ 5 phút | chỉ reset khi tắt đủ 5 phút |
| **Hồi dần** (mặc định) | còn 12 phút, dùng tiếp | 1 phút tắt = (hạn mức ÷ ngưỡng reset) phút dùng, ở đây là 3; tắt đủ 5 phút thì hồi đầy — trùng với reset |
| Trừ vào quãng nghỉ | dùng nốt 10 giây, rồi chỉ nghỉ 1 phút | trừ quãng tắt **dài nhất** trong đợt; phần còn phải nghỉ dưới 30 giây thì không khoá, coi như nghỉ đủ |

Cả hai cách sau chỉ tính những lần tắt màn hình **từ 1 phút trở lên** — tắt vài
giây nhìn thông báo không phải là nghỉ, nếu không "dùng 45 giây, tắt 15 giây"
sẽ thành dùng liên tục trá hình.

| Thiết lập | Mặc định | Ý nghĩa |
|---|---|---|
| Bật dùng ngắt quãng | tắt | công tắc chính |
| Dùng liên tục mỗi đợt | 15 phút | hạn mức cộng dồn của một đợt |
| Nghỉ bắt buộc | 5 phút | dài bao lâu thì được dùng lại |
| Ngưỡng reset bộ đếm | 5 phút | nghỉ lâu hơn chừng này thì đếm lại từ 0 |
| Khi tắt chưa đủ ngưỡng | hồi dần | ba cách tính ở bảng trên |
| Cảnh báo trước khi hết đợt | 2 phút | phải nhỏ hơn hạn mức mỗi đợt |
| Khung giờ áp dụng | cả ngày | để hai ô giờ giống nhau nghĩa là cả ngày |
| Thoát khẩn cấp mỗi ngày | 2 lượt | đặt 0 là không cho thoát |
| Không tắt màn hình khi đang gọi điện | bật | áp dụng cho cả khoá ban đêm |

**Thoát khẩn cấp** có vì một lý do nghiêm túc: lớp phủ che kín màn hình, kể cả
màn hình cuộc gọi đến. Phải bấm **hai lần trong 5 giây** mới thoát được, mỗi lần
đều bị ghi vào nhật ký và trừ vào hạn mức trong ngày. Ngoài ra khi máy đang trong
cuộc gọi thì app không tắt màn hình — kiểm bằng `AudioManager.getMode()`, không
cần xin thêm quyền nào. Việc hoãn này có **trần 20 phút**: `MODE_IN_COMMUNICATION`
không chỉ có cuộc gọi thật, khối ứng dụng ghi âm và trợ lý giọng nói cũng giữ chế
độ đó, hoãn vô hạn thì mở một ứng dụng như thế lên là thoát khoá cả đêm.

Nút **Xem thống kê hôm nay** cho biết tổng thời gian đã dùng, số đợt nghỉ, số lần
thoát khẩn cấp, và đợt hiện tại đã đi được bao xa.

Toàn bộ trạng thái đếm nằm trong `SharedPreferences` chứ không nằm trong bộ nhớ,
nên hệ thống có giết dịch vụ rồi dựng lại thì vẫn đếm tiếp đúng chỗ cũ.

## Cấu trúc mã nguồn

| Tệp | Việc |
|---|---|
| `ChinhActivity.java` | màn hình chính: ba tab Ghi nhận / Thống kê / Cài đặt |
| `LuatGiacNgu.java` | luật thưởng phạt và chuỗi. Bản port của `js/rules.js`, đối chiếu 90/90 dòng |
| `CaiDatNgu.java` `DemNgu.java` | mô hình dữ liệu module Giấc ngủ |
| `KhoGiacNgu.java` | lưu cấu hình và danh sách đêm ra JSON, tính lại, đánh dấu cần đồng bộ |
| `DongBo.java` | HTTP tới Apps Script Web App, tự đi theo chuyển hướng |
| `NhacNgu.java` | hai lời nhắc mỗi đêm, im lặng nếu đêm đó đã ghi |
| `BieuDo.java` | biểu đồ 14 đêm, vẽ tay bằng Canvas |
| `CauHinh.java` | cấu hình khoá máy + phép tính mốc thời gian + băm mã |
| `NgatQuang.java` | luật dùng ngắt quãng. Phần tính toán là hàm tĩnh, kiểm thử được bằng Java thuần |
| `LenLich.java` | đặt báo thức chính xác cho lần khoá và từng mốc cảnh báo |
| `BaoThucReceiver.java` | báo thức nổ → chuyển việc sang dịch vụ |
| `DichVuKhoa.java` | dịch vụ nền: lớp phủ khoá, lớp phủ nghỉ, theo dõi bật/tắt màn hình |
| `KhungPhu.java` | nuốt phím Quay lại khi đang khoá |
| `QuanTriReceiver.java` | quyền quản trị thiết bị, gọi `lockNow()` |
| `KhoiDongReceiver.java` | khởi động lại máy xong thì dựng lại lịch |
| `CaiDatActivity.java` | màn hình cài đặt, cũng là màn hình chính |
| `NhatKy.java` | ghi CSV mọi lần khoá / mở / nhập sai / né tránh |
| `GiaoDien.java` | né thanh hệ thống, đo bàn phím, bảng chọn ngày giờ, chuẩn hoá giờ gõ tay |
| `ManKiemSoat.java` | điều khiển tab Khoá máy — trước là Activity riêng, nay là tab thứ tư |
| `CapNhat.java` | tự tìm bản mới, tải, mở trình cài đặt |
| `NhaCungCapApk.java` | cấp tệp APK cho trình cài đặt, thay cho FileProvider của AndroidX |

Java thuần, **không phụ thuộc thư viện ngoài nào** — không AndroidX, không
Capacitor. Nhờ vậy APK chỉ 114 KB và dựng được mà không cần tải gì thêm.

Vì sao dùng lớp phủ chứ không dùng Activity: từ Android 10, app chạy nền không
được tự mở Activity. Lớp phủ `TYPE_APPLICATION_OVERLAY` thì dựng lúc nào cũng
được, lại nằm đè lên cả màn hình chính — nên bấm Home cũng không thoát ra được.

## Chỗ này khoá được tới đâu

Công cụ tạo **ma sát**, không phải khoá an ninh. Vẫn thoát được nếu quyết tâm:
Cài đặt → gỡ quyền quản trị thiết bị, hoặc gỡ hẳn app. App chống lại những đường
thoát dễ: phím Quay lại bị nuốt, phím Home vô dụng vì lớp phủ đè lên trên, để đó
không nhập mã thì cứ 2 phút màn hình tắt tiếp, khởi động lại máy cũng vô ích vì
`KhoiDongReceiver` dựng lại lịch và khoá sau 1 phút. Mọi lần né tránh — kể cả lần
gỡ quyền quản trị — đều bị ghi vào nhật ký, xem bằng nút **Xem nhật ký**.

## Yêu cầu dựng lại

- JDK 21 (máy này có ở `C:/AI_Project/jdk21` — khai trong `gradle.properties`)
- Android SDK platform 36 + build-tools 36
- Gradle 8.14.3 và AGP 8.13.0 (đã có sẵn trong cache của máy)

`local.properties` chứa đường dẫn SDK của riêng máy, đã bị `.gitignore` loại ra.
