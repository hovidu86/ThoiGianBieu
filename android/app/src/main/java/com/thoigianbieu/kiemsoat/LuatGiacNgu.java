package com.thoigianbieu.kiemsoat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Luật thưởng/phạt, chuỗi kỷ luật và các phép tính của module Giấc ngủ.
 *
 * Chuyển nguyên từ rules.js của bản PWA. Không đụng tới giao diện, không đụng
 * tới Android — chạy được trên Java thuần nên kiểm thử được từng luật một.
 */
public class LuatGiacNgu {

    /**
     * Giờ trước mốc này được coi là rạng sáng của đêm hôm trước.
     * Nhờ vậy 01:30 thành 25.5 và so sánh liên tục được với 22:00 thành 22.
     */
    public static final int MOC_CHIA_NGAY = 18;

    /** Kết quả đánh giá một giờ đi ngủ. */
    public static class KetQua {
        public long tien;
        public String loi;
        public String viec;      // "cong" | "giu" | "xoa"
        public double giaTri;
    }

    /** Kết quả tính lại toàn bộ danh sách. */
    public static class BangTinh {
        public List<DemNgu> danhSach = new ArrayList<>();   // mới nhất trước
        public int chuoiDaiNhat;
        public int chuoiHienTai;
    }

    /* ==================== THỜI GIAN ==================== */

    /** "23:45" thành 23.75 ; "01:30" thành 25.5 (thuộc đêm hôm trước). */
    public static double giaTri(String gio) {
        if (gio == null || gio.isEmpty()) return Double.NaN;
        String[] p = gio.split(":");
        try {
            double v = Integer.parseInt(p[0].trim())
                    + (p.length > 1 ? Integer.parseInt(p[1].trim()) : 0) / 60.0;
            if (v < MOC_CHIA_NGAY) v += 24;
            return v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 25.5 thành "01:30". */
    public static String giaTriRaGio(double v) {
        if (Double.isNaN(v)) return "--:--";
        double x = ((v % 24) + 24) % 24;
        int h = (int) Math.floor(x);
        int m = (int) Math.round((x - h) * 60);
        if (m == 60) { m = 0; h = (h + 1) % 24; }
        return hai(h) + ":" + hai(m);
    }

    public static String hai(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    public static String ngayISO(Calendar c) {
        return c.get(Calendar.YEAR) + "-" + hai(c.get(Calendar.MONTH) + 1)
                + "-" + hai(c.get(Calendar.DAY_OF_MONTH));
    }

    /** "Đêm" của thời điểm này. Trước 18:00 thì vẫn tính là đêm hôm qua. */
    public static String demHienTai(long luc) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(luc);
        if (c.get(Calendar.HOUR_OF_DAY) < MOC_CHIA_NGAY) c.add(Calendar.DAY_OF_MONTH, -1);
        return ngayISO(c);
    }

    public static String demHienTai() {
        return demHienTai(System.currentTimeMillis());
    }

    /** Giờ hiện tại dạng HH:mm. */
    public static String gioHienTai(long luc) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(luc);
        return hai(c.get(Calendar.HOUR_OF_DAY)) + ":" + hai(c.get(Calendar.MINUTE));
    }

    /* ==================== LUẬT ==================== */

    private static List<CaiDatNgu.Muc> mucDaSap(CaiDatNgu cd) {
        List<CaiDatNgu.Muc> ra = new ArrayList<>();
        for (CaiDatNgu.Muc m : cd.cacMuc) {
            if (m != null && m.den != null && !m.den.isEmpty()) ra.add(m);
        }
        Collections.sort(ra, new Comparator<CaiDatNgu.Muc>() {
            @Override
            public int compare(CaiDatNgu.Muc a, CaiDatNgu.Muc b) {
                return Double.compare(giaTri(a.den), giaTri(b.den));
            }
        });
        return ra;
    }

    /** Đánh giá một giờ đi ngủ. Trả về null nếu giờ không hợp lệ. */
    public static KetQua danhGia(String gio, CaiDatNgu cd) {
        double v = giaTri(gio);
        if (Double.isNaN(v)) return null;

        List<CaiDatNgu.Muc> muc = mucDaSap(cd);
        KetQua kq = new KetQua();
        kq.giaTri = v;

        boolean trung = false;
        for (CaiDatNgu.Muc m : muc) {
            if (v <= giaTri(m.den)) {
                kq.tien = m.tien;
                kq.loi = m.loi == null ? "" : m.loi;
                trung = true;
                break;
            }
        }

        if (!trung) {
            // Quá mốc cuối: cộng dồn tiền phạt theo từng giờ trôi qua.
            CaiDatNgu.Muc cuoi = muc.isEmpty() ? null : muc.get(muc.size() - 1);
            double vCuoi = cuoi != null ? giaTri(cuoi.den) : 24;
            long goc = cuoi != null ? cuoi.tien : 0;
            long soGio = (long) Math.floor(v - vCuoi) + 1;
            kq.tien = goc + soGio * cd.phatMoiGioVuot;
            kq.loi = "Kịch khung phạt - phá hủy sức khỏe!";
        }

        double dungGio = giaTri(cd.truocGioNay);
        double xoaChuoi = giaTri(cd.matChuoiSau);
        if (v <= dungGio) kq.viec = "cong";
        else if (v > xoaChuoi) kq.viec = "xoa";
        else kq.viec = "giu";

        return kq;
    }

    /** Thưởng mốc chuỗi, nếu chuỗi vừa chạm đúng một mốc. */
    public static long thuongMocChuoi(int chuoi, CaiDatNgu cd) {
        for (CaiDatNgu.MocChuoi m : cd.cacMocChuoi) {
            if (m.ngay == chuoi) return m.tien;
        }
        return 0;
    }

    public static String loiMocChuoi(int chuoi, CaiDatNgu cd) {
        return thuongMocChuoi(chuoi, cd) != 0 ? "Thưởng chuỗi " + chuoi + " ngày!" : "";
    }

    /**
     * Tính lại chuỗi và tiền cho toàn bộ danh sách, theo thứ tự ngày tăng dần.
     * Nhờ vậy sửa hay xoá một đêm cũ vẫn cho kết quả đúng ở mọi đêm sau nó.
     */
    public static BangTinh tinhLai(List<DemNgu> dau, CaiDatNgu cd) {
        List<DemNgu> tang = new ArrayList<>(dau);
        Collections.sort(tang, new Comparator<DemNgu>() {
            @Override
            public int compare(DemNgu a, DemNgu b) { return a.ngay.compareTo(b.ngay); }
        });

        int chuoi = 0, dai = 0;
        int soBuoc = cd.cacBuoc.size();

        for (DemNgu d : tang) {
            KetQua kq = danhGia(d.gio, cd);
            if (kq == null) continue;

            if ("cong".equals(kq.viec)) chuoi += 1;
            else if ("xoa".equals(kq.viec)) chuoi = 0;

            long thuong = "cong".equals(kq.viec) ? thuongMocChuoi(chuoi, cd) : 0;
            String loiThuong = "cong".equals(kq.viec) ? loiMocChuoi(chuoi, cd) : "";

            d.thuongThoiQuen = (soBuoc > 0 && d.soBuocXong >= soBuoc) ? cd.thuongThoiQuen : 0;
            d.tienGoc = kq.tien;
            d.thuongChuoi = thuong;
            d.loiChuoi = loiThuong;
            d.tongTien = kq.tien + thuong + d.thuongThoiQuen;
            d.loi = kq.loi;
            d.chuoiSauDem = chuoi;
            d.tre = !"cong".equals(kq.viec);
            d.giaTri = kq.giaTri;
            d.thoiLuong = thoiLuong(d.gio, d.gioDay);

            if (chuoi > dai) dai = chuoi;
        }

        Collections.reverse(tang);
        BangTinh bt = new BangTinh();
        bt.danhSach = tang;
        bt.chuoiDaiNhat = dai;
        bt.chuoiHienTai = chuoi;
        return bt;
    }

    /** Số giờ ngủ giữa giờ lên giường và giờ thức dậy. null nếu thiếu dữ liệu. */
    public static Double thoiLuong(String gioNgu, String gioDay) {
        if (gioNgu == null || gioNgu.isEmpty() || gioDay == null || gioDay.isEmpty()) return null;
        double b = giaTri(gioNgu);
        String[] p = gioDay.split(":");
        double w;
        try {
            w = Integer.parseInt(p[0].trim())
                    + (p.length > 1 ? Integer.parseInt(p[1].trim()) : 0) / 60.0 + 24;
        } catch (NumberFormatException e) {
            return null;
        }
        double d = w - b;
        while (d < 0) d += 24;
        if (d > 16) d -= 24;
        return d > 0 ? Math.round(d * 100) / 100.0 : null;
    }

    /* ==================== ĐỊNH DẠNG ==================== */

    private static DecimalFormat dinhDangTien() {
        DecimalFormatSymbols ky = new DecimalFormatSymbols(Locale.US);
        ky.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", ky);
    }

    /** 50000 thành "50.000 ₫". */
    public static String tien(long n) {
        return dinhDangTien().format(n) + " ₫";
    }

    /** Như trên nhưng số dương có dấu cộng ở trước. */
    public static String tienCoDau(long n) {
        return (n > 0 ? "+" : "") + tien(n);
    }

    /** "2026-08-27" thành "27/08". */
    public static String ngayNgan(String iso) {
        String[] p = String.valueOf(iso).split("-");
        return p.length >= 3 ? p[2] + "/" + p[1] : String.valueOf(iso);
    }

    /** Khoảng cách người đọc được: "2 giờ 15 phút". */
    public static String khoangCach(long ms) {
        long phut = Math.max(0, ms / 60_000L);
        if (phut < 60) return phut + " phút";
        return (phut / 60) + " giờ " + (phut % 60) + " phút";
    }

    /** Thời điểm tới của mốc HH:mm gần nhất kể từ bây giờ. */
    public static long mocKeTiep(String hhmm, long bayGio) {
        String[] p = hhmm.split(":");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(bayGio);
        try {
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0].trim()));
            c.set(Calendar.MINUTE, p.length > 1 ? Integer.parseInt(p[1].trim()) : 0);
        } catch (NumberFormatException e) {
            return bayGio;
        }
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= bayGio) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** Chỉ để hiển thị: "27/08/2026". */
    public static String ngayDayDu(String iso) {
        String[] p = String.valueOf(iso).split("-");
        return p.length >= 3 ? p[2] + "/" + p[1] + "/" + p[0] : String.valueOf(iso);
    }
}
