package com.thoigianbieu.kiemsoat;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình module Giấc ngủ. Chỉ chứa dữ liệu, không biết gì về JSON hay
 * SharedPreferences — phần đó nằm ở {@link KhoGiacNgu}. Nhờ vậy toàn bộ luật
 * tính tiền kiểm thử được bằng Java thuần.
 *
 * Chuyển nguyên từ DEFAULT_SETTINGS của bản PWA, không đổi con số nào.
 */
public class CaiDatNgu {

    /** Một mốc giờ và mức thưởng/phạt tương ứng. */
    public static class Muc {
        public String den;      // "22:30"
        public long tien;       // 30000 hoặc -50000
        public String loi;

        public Muc(String den, long tien, String loi) {
            this.den = den; this.tien = tien; this.loi = loi;
        }
    }

    /** Mốc thưởng theo số ngày giữ chuỗi. */
    public static class MocChuoi {
        public int ngay;
        public long tien;

        public MocChuoi(int ngay, long tien) { this.ngay = ngay; this.tien = tien; }
    }

    /** Một bước trong checklist chuẩn bị ngủ. */
    public static class Buoc {
        public String nhan;
        public int phut;

        public Buoc(String nhan, int phut) { this.nhan = nhan; this.phut = phut; }
    }

    /** URL Web App của Google Apps Script. Không bao giờ ghi sẵn vào mã nguồn. */
    public String urlWebApp = "";

    public List<Muc> cacMuc = new ArrayList<>();
    public long phatMoiGioVuot = -100_000L;
    public String truocGioNay = "22:30";   // ngủ trước mốc này thì chuỗi +1
    public String matChuoiSau = "23:30";   // muộn hơn mốc này thì chuỗi về 0
    public List<MocChuoi> cacMocChuoi = new ArrayList<>();
    public String nhacLuc = "21:30";
    public int phutChuanBi = 45;
    public long thuongThoiQuen = 20_000L;
    public List<Buoc> cacBuoc = new ArrayList<>();

    public static CaiDatNgu macDinh() {
        CaiDatNgu c = new CaiDatNgu();
        c.cacMuc.add(new Muc("22:00",   50_000L, "Tuyệt vời! Ngủ rất sớm."));
        c.cacMuc.add(new Muc("22:30",   30_000L, "Tốt! Đạt mục tiêu."));
        c.cacMuc.add(new Muc("23:00",         0, "An toàn. Không thưởng phạt."));
        c.cacMuc.add(new Muc("23:30",  -20_000L, "Hơi trễ. Bị phạt nhẹ."));
        c.cacMuc.add(new Muc("24:00",  -50_000L, "Trễ! Phạt nặng và mất chuỗi."));
        c.cacMuc.add(new Muc("25:00", -100_000L, "Quá trễ. Phạt 100k."));
        c.cacMuc.add(new Muc("26:00", -150_000L, "Cú đêm. Phạt 150k."));
        c.cacMuc.add(new Muc("27:00", -250_000L, "Rất nguy hại cho sức khỏe!"));

        c.cacMocChuoi.add(new MocChuoi(3,    100_000L));
        c.cacMocChuoi.add(new MocChuoi(7,    300_000L));
        c.cacMocChuoi.add(new MocChuoi(14,   700_000L));
        c.cacMocChuoi.add(new MocChuoi(30, 2_000_000L));

        c.cacBuoc.add(new Buoc("Cất điện thoại ra khỏi giường", 0));
        c.cacBuoc.add(new Buoc("Vận động nhẹ / giãn cơ", 10));
        c.cacBuoc.add(new Buoc("Ngồi máy massage", 15));
        c.cacBuoc.add(new Buoc("Tắm nước ấm / vệ sinh cá nhân", 10));
        c.cacBuoc.add(new Buoc("Giảm đèn, không màn hình", 0));
        return c;
    }
}
