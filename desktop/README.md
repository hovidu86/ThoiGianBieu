# Kiểm soát máy — Windows

Module Windows của *Thời gian biểu*. Tới giờ đã hẹn thì tắt màn hình và khoá máy;
muốn dùng lại phải nhập mã dài **hai lần, cách nhau 15 giây**. Sau đó cứ mỗi 15 phút
lại khoá tiếp, cho tới giờ kết thúc.

## Cài đặt

Nhấn đúp **`Cai-dat.cmd`**. File tự xin quyền quản trị (bấm *Yes*), rồi:

1. Đăng ký tác vụ `ThoiGianBieu-KiemSoatMay` trong Task Scheduler.
2. Kiểm lại từng tuỳ chọn vừa đặt và in ra `[OK]` / `[HONG]` cho từng cái.
3. Khởi động app qua chính tác vụ đó — vừa là phép thử thật, vừa để app chạy
   với quyền thường chứ không thừa hưởng quyền quản trị của bộ cài.

Lần đầu chạy **bắt buộc đặt mã mở khoá tối thiểu 10 ký tự, gõ giống nhau ở cả
hai ô**. Thiếu gì app hiện hộp thoại nói rõ lý do. Chưa có mã thì app không khoá
gì cả, và khay hệ thống ghi thẳng "CHƯA ĐẶT MÃ".

### Vì sao phải dựng tác vụ bằng XML

`schtasks` dựng bằng dòng lệnh để lại bốn mặc định giết chết tính năng tự chạy:

| Mặc định | Hậu quả |
|---|---|
| `DisallowStartIfOnBatteries = true` | **laptop rút sạc là không bao giờ tự chạy** |
| `StopIfGoingOnBatteries = true` | đang chạy mà rút sạc thì bị giết |
| `ExecutionTimeLimit = 72 giờ` | quá 3 ngày là bị giết |
| chỉ có một mốc kích hoạt lúc đăng nhập | app tắt giữa chừng thì thôi luôn |

Bộ cài hiện tại đặt ba cái đầu về `false`/`PT0S`, và thêm **lịch lặp mỗi 30
phút**: app tắt vì bất cứ lý do gì thì chậm nhất nửa tiếng sau tự sống lại. Bản
đang chạy giữ một mutex nên các lần gọi thừa tự thoát lặng lẽ, không nhân bản.

Gỡ bỏ: nhấn đúp `Go-cai-dat.cmd`, rồi chuột phải icon khiên ở khay hệ thống →
*Thoát (cần mã)*.

## Dùng hằng ngày

Icon hình khiên nằm ở khay hệ thống, cạnh đồng hồ. Chuột phải để mở menu:

| Mục | Việc |
|---|---|
| Lần khoá kế tiếp | chỉ để xem, không bấm được |
| Cài đặt… | đổi giờ giấc, mốc cảnh báo, mã mở khoá |
| Khoá ngay bây giờ | thử xem màn hình khoá trông thế nào |
| Mở nhật ký | xem file CSV ghi mọi lần khoá / mở / nhập sai |
| Thoát (cần mã) | phải nhập đúng mã mới tắt được app |

Diễn biến một đêm, với cấu hình mặc định:

- **22:30** — cảnh báo "còn 15 phút", kèm tiếng chuông.
- **22:40** — cảnh báo "còn 5 phút".
- **22:44** — cảnh báo "còn 1 phút".
- **22:45** — màn hình tắt, phiên Windows bị khoá.
- Muốn dùng lại: đăng nhập Windows như thường → gặp ngay màn hình đen đòi mã →
  nhập mã → chờ 15 giây → nhập lại mã đó → mở.
- **23:00** — khoá lại. Cứ thế mỗi 15 phút.
- **05:30** — hết khoảng khoá, thôi khoá cho tới 22:45 tối hôm sau.

Nhập sai ở lần 2 thì phải làm lại từ đầu, kể cả 15 giây chờ.

## Cấu hình

Sửa trong cửa sổ Cài đặt. File lưu tại:

```
%LOCALAPPDATA%\ThoiGianBieu\may.json
```

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `gioKhoa` | `22:45` | giờ khoá lần đầu trong đêm |
| `gioKetThuc` | `05:30` | sau mốc này thôi khoá |
| `lapLaiPhut` | `15` | mở khoá xong bao lâu thì khoá lại |
| `canhBaoPhut` | `[15, 5, 1]` | các mốc cảnh báo trước khi khoá |
| `khoangCachGiay` | `15` | hai lần nhập mã phải cách nhau chừng này |
| `doDaiMaToiThieu` | `10` | độ dài tối thiểu của mã |
| `maBam` | — | SHA-256 của mã. **Mã gốc không được lưu ở đâu cả** |
| `khoaWindows` | `true` | có khoá luôn phiên Windows không |

Nhật ký nằm cạnh đó: `nhat-ky-may.csv`.

## Quên mã thì sao

Không có cửa hậu — mã chỉ lưu dạng băm. Cách duy nhất: kết thúc tiến trình
`powershell.exe` trong Task Manager, xoá file `may.json`, chạy lại `Cai-dat.cmd`
và đặt mã mới.

## Chỗ này khoá được tới đâu

Đây là công cụ tạo **ma sát**, không phải khoá an ninh. Người quyết tâm vẫn thoát
được: Ctrl+Shift+Esc để kết thúc tiến trình, hoặc khởi động lại máy. App chống lại
những đường thoát dễ nhất — Alt+F4 bị chặn, cửa sổ khoá tự giành lại tiêu điểm khi
bị click ra ngoài, muốn thoát qua khay hệ thống phải có mã, và mọi lần né tránh đều
bị ghi vào nhật ký. Khởi động lại máy trong khoảng khoá cũng vô ích: app tự chạy
lại lúc đăng nhập và khoá lại sau 1 phút.

## Yêu cầu

Windows PowerShell 5.1 (có sẵn trong Windows 10/11). Không cần cài thêm gì,
không cần biên dịch. Toàn bộ nằm trong `KiemSoatMay.ps1`, sửa được bằng Notepad.
