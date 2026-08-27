package com.thoigianbieu.kiemsoat;

/**
 * Một đêm đã ghi nhận.
 *
 * Bốn trường đầu là dữ liệu người dùng nhập, phần còn lại do
 * {@link LuatGiacNgu#tinhLai} tính ra và bị ghi đè mỗi lần tính lại — sửa một
 * đêm cũ thì mọi đêm sau nó đều được tính lại cho đúng.
 */
public class DemNgu {

    public String ngay = "";     // YYYY-MM-DD, là "đêm" chứ không phải ngày lịch
    public String gio = "";      // giờ lên giường, HH:mm
    public String gioDay = "";   // giờ thức dậy, có thể để trống
    public int soBuocXong;
    public boolean daGui;        // đã đẩy lên Google Sheets chưa
    public boolean daXacNhan;    // Sheets có trả lời xác nhận không

    /* --- tính ra, đừng sửa tay --- */
    public long tienGoc;
    public long thuongChuoi;
    public long thuongThoiQuen;
    public long tongTien;
    public int chuoiSauDem;
    public String loi = "";
    public String loiChuoi = "";
    public boolean tre;
    public double giaTri = Double.NaN;   // 23:45 -> 23.75 ; 01:30 -> 25.5
    public Double thoiLuong;             // số giờ ngủ, null nếu chưa có giờ dậy

    public DemNgu() { }

    public DemNgu(String ngay, String gio, String gioDay, int soBuocXong) {
        this.ngay = ngay;
        this.gio = gio;
        this.gioDay = gioDay == null ? "" : gioDay;
        this.soBuocXong = soBuocXong;
    }

    /**
     * Dấu vân tay của kết quả. Đổi dấu này nghĩa là đêm đó cần đẩy lại lên
     * Sheets, kể cả khi người dùng không đụng gì vào nó — ví dụ sửa mốc tiền
     * làm mọi đêm cũ đổi kết quả.
     */
    public String dauVan() {
        return ngay + "|" + gio + "|" + gioDay + "|" + tongTien + "|" + chuoiSauDem;
    }
}
