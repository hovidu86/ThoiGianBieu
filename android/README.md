# Kiểm soát máy — Android

Module Android của *Thời gian biểu*. Hai tính năng:

1. **Khoá theo giờ** — giống hệt bản Windows: tới giờ đã hẹn thì tắt màn hình,
   muốn dùng lại phải nhập mã dài **hai lần, cách nhau 15 giây**, sau đó cứ mỗi
   15 phút lại khoá tiếp cho tới giờ kết thúc. Gác giờ đi ngủ.
2. **Dùng ngắt quãng** — chia thời gian dùng máy thành từng đợt rời rạc, hết hạn
   mức thì bắt nghỉ. Chống việc trôi cả tiếng trong TikTok hay Shorts.

**Vì sao không phải PWA:** trình duyệt không có API nào tắt được màn hình hay khoá
thiết bị. Web chỉ có `WakeLock` để *giữ* màn hình sáng — đúng chiều ngược lại.
Bắt buộc phải là app gốc, dùng quyền quản trị thiết bị (`DevicePolicyManager.lockNow`).

## Cài lên điện thoại

APK dựng sẵn nằm ở `dist-apk/KiemSoatMay.apk` (47 KB).

1. Chép file APK sang điện thoại — qua cáp, Zalo, Google Drive, hoặc tải thẳng từ
   GitHub bằng trình duyệt trên máy.
2. Mở file → Android hỏi *"Cài ứng dụng không rõ nguồn gốc"* → cho phép nguồn đó.
3. Mở app **Kiểm soát máy**.

Cần dựng lại APK sau khi sửa mã: nhấn đúp `Dung-apk.cmd`, hoặc chạy
`gradlew assembleRelease`.

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

| Thiết lập | Mặc định | Ý nghĩa |
|---|---|---|
| Bật dùng ngắt quãng | tắt | công tắc chính |
| Dùng liên tục mỗi đợt | 15 phút | hạn mức cộng dồn của một đợt |
| Nghỉ bắt buộc | 5 phút | dài bao lâu thì được dùng lại |
| Ngưỡng reset bộ đếm | 5 phút | nghỉ lâu hơn chừng này thì đếm lại từ 0 |
| Cảnh báo trước khi hết đợt | 2 phút | phải nhỏ hơn hạn mức mỗi đợt |
| Khung giờ áp dụng | cả ngày | để hai ô giờ giống nhau nghĩa là cả ngày |
| Thoát khẩn cấp mỗi ngày | 2 lượt | đặt 0 là không cho thoát |
| Không tắt màn hình khi đang gọi điện | bật | áp dụng cho cả khoá ban đêm |

**Thoát khẩn cấp** có vì một lý do nghiêm túc: lớp phủ che kín màn hình, kể cả
màn hình cuộc gọi đến. Phải bấm **hai lần trong 5 giây** mới thoát được, mỗi lần
đều bị ghi vào nhật ký và trừ vào hạn mức trong ngày. Ngoài ra khi máy đang trong
cuộc gọi thì app không tắt màn hình — kiểm bằng `AudioManager.getMode()`, không
cần xin thêm quyền nào.

Nút **Xem thống kê hôm nay** cho biết tổng thời gian đã dùng, số đợt nghỉ, số lần
thoát khẩn cấp, và đợt hiện tại đã đi được bao xa.

Toàn bộ trạng thái đếm nằm trong `SharedPreferences` chứ không nằm trong bộ nhớ,
nên hệ thống có giết dịch vụ rồi dựng lại thì vẫn đếm tiếp đúng chỗ cũ.

## Cấu trúc mã nguồn

| Tệp | Việc |
|---|---|
| `CauHinh.java` | cấu hình + toàn bộ phép tính mốc thời gian + băm mã. Không đụng giao diện |
| `NgatQuang.java` | luật dùng ngắt quãng. Phần tính toán là hàm tĩnh, kiểm thử được bằng Java thuần |
| `LenLich.java` | đặt báo thức chính xác cho lần khoá và từng mốc cảnh báo |
| `BaoThucReceiver.java` | báo thức nổ → chuyển việc sang dịch vụ |
| `DichVuKhoa.java` | dịch vụ nền: lớp phủ khoá, lớp phủ nghỉ, theo dõi bật/tắt màn hình |
| `KhungPhu.java` | nuốt phím Quay lại khi đang khoá |
| `QuanTriReceiver.java` | quyền quản trị thiết bị, gọi `lockNow()` |
| `KhoiDongReceiver.java` | khởi động lại máy xong thì dựng lại lịch |
| `CaiDatActivity.java` | màn hình cài đặt, cũng là màn hình chính |
| `NhatKy.java` | ghi CSV mọi lần khoá / mở / nhập sai / né tránh |

Java thuần, **không phụ thuộc thư viện ngoài nào** — không AndroidX, không
Capacitor. Nhờ vậy APK chỉ 47 KB và dựng được mà không cần tải gì thêm.

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
