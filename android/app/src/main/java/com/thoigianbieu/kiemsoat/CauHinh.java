package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Cấu hình và toàn bộ phép tính mốc thời gian cho cả hai tính năng:
 * khoá theo giờ ban đêm, và dùng ngắt quãng ban ngày.
 * Không đụng tới giao diện, nhờ vậy kiểm thử được độc lập.
 */
public class CauHinh {

    private static final String TEP = "kiem_soat_may";
    public static final int DO_DAI_MA_TOI_THIEU = 10;
    private static final long MOT_NGAY = 24L * 60 * 60 * 1000;

    private final SharedPreferences p;

    public CauHinh(Context ctx) {
        p = ctx.getApplicationContext().getSharedPreferences(TEP, Context.MODE_PRIVATE);
    }

    /* ==================== KHOÁ THEO GIỜ ==================== */

    public String gioKhoa()      { return p.getString("gioKhoa", "22:45"); }
    public String gioKetThuc()   { return p.getString("gioKetThuc", "05:30"); }
    public int lapLaiPhut()      { return p.getInt("lapLaiPhut", 15); }
    public int khoangCachGiay()  { return p.getInt("khoangCachGiay", 15); }
    public String canhBaoPhut()  { return p.getString("canhBaoPhut", "15,5,1"); }
    public String maBam()        { return p.getString("maBam", ""); }
    public boolean daDatMa()     { return !maBam().isEmpty(); }
    public long mocKhoa()        { return p.getLong("mocKhoa", 0L); }
    /** Người dùng có thể tắt hẳn khoá theo giờ mà vẫn giữ nguyên mã và giờ đã đặt. */
    public boolean khoaTheoGioBat() { return p.getBoolean("khoaTheoGioBat", true); }
    /**
     * Lúc khoá đêm hoặc vào quãng nghỉ bắt buộc, có giành quyền phát âm thanh
     * để buộc app khác (YouTube, nhạc...) dừng phát không — mặc định có, vì
     * tắt màn hình không hề dừng âm thanh, để mặc kệ thì kẹt cứng không tắt
     * được gì. Dùng chung cho cả khoá theo giờ lẫn dùng ngắt quãng.
     */
    public boolean chanAmThanhBat() { return p.getBoolean("chanAmThanhBat", true); }

    public void datChanAmThanh(boolean bat) {
        p.edit().putBoolean("chanAmThanhBat", bat).apply();
    }

    public void luu(String gioKhoa, String gioKetThuc, int lapLaiPhut,
                    String canhBaoPhut, int khoangCachGiay, boolean khoaTheoGioBat) {
        p.edit()
                .putString("gioKhoa", gioKhoa)
                .putString("gioKetThuc", gioKetThuc)
                .putInt("lapLaiPhut", lapLaiPhut)
                .putString("canhBaoPhut", canhBaoPhut)
                .putInt("khoangCachGiay", khoangCachGiay)
                .putBoolean("khoaTheoGioBat", khoaTheoGioBat)
                .apply();
    }

    public void datMa(String maGoc) {
        p.edit().putString("maBam", bam(maGoc)).apply();
    }

    public boolean maDung(String thu) {
        String bam = bam(thu);
        return !bam.isEmpty() && bam.equals(maBam());
    }

    public void datMocKhoa(long moc) {
        p.edit().putLong("mocKhoa", moc).apply();
    }

    /**
     * Danh sách mốc cảnh báo đã thật sự đặt báo thức lần trước. Phải nhớ lại
     * đúng danh sách này mới huỷ hết được: người dùng sửa "15,5,1" thành "3"
     * mà chỉ huỷ theo danh sách mới thì hai báo thức 15 và 5 phút vẫn còn đó
     * và sẽ nổ ra cảnh báo ma.
     */
    public List<Integer> mocCanhBaoDaDat() {
        return tachSo(p.getString("canhBaoDaDat", ""));
    }

    public void luuMocCanhBaoDaDat(List<Integer> ds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ds.get(i));
        }
        p.edit().putString("canhBaoDaDat", sb.toString()).apply();
    }

    /* ==================== DÙNG NGẮT QUÃNG ==================== */

    public boolean ngatQuangBat()     { return p.getBoolean("nqBat", false); }
    public int nqPhutDung()           { return p.getInt("nqPhutDung", 15); }
    public int nqPhutNghi()           { return p.getInt("nqPhutNghi", 5); }
    public int nqPhutReset()          { return p.getInt("nqPhutReset", 5); }
    public int nqCanhBaoPhut()        { return p.getInt("nqCanhBaoPhut", 2); }
    public String nqTuGio()           { return p.getString("nqTuGio", "00:00"); }
    public String nqDenGio()          { return p.getString("nqDenGio", "00:00"); }
    public int nqKhanCapMoiNgay()     { return p.getInt("nqKhanCapMoiNgay", 2); }
    public boolean nqKhongKhoaKhiGoi(){ return p.getBoolean("nqKhongKhoaKhiGoi", true); }

    public void luuNgatQuang(boolean bat, int phutDung, int phutNghi, int phutReset,
                             int canhBaoPhut, String tuGio, String denGio,
                             int khanCapMoiNgay, boolean khongKhoaKhiGoi) {
        p.edit()
                .putBoolean("nqBat", bat)
                .putInt("nqPhutDung", phutDung)
                .putInt("nqPhutNghi", phutNghi)
                .putInt("nqPhutReset", phutReset)
                .putInt("nqCanhBaoPhut", canhBaoPhut)
                .putString("nqTuGio", tuGio)
                .putString("nqDenGio", denGio)
                .putInt("nqKhanCapMoiNgay", khanCapMoiNgay)
                .putBoolean("nqKhongKhoaKhiGoi", khongKhoaKhiGoi)
                .apply();
    }

    /* --- trạng thái đợt dùng hiện tại, phải sống sót qua việc dịch vụ bị giết --- */

    public long nqDaDungMs()      { return p.getLong("nqDaDungMs", 0L); }
    public long nqBatDauPhien()   { return p.getLong("nqBatDauPhien", 0L); }
    public long nqLucTatManHinh() { return p.getLong("nqLucTat", 0L); }
    public long nqKetThucNghi()   { return p.getLong("nqKetThucNghi", 0L); }

    public void nqDatTrangThai(long daDungMs, long batDauPhien, long lucTat) {
        p.edit()
                .putLong("nqDaDungMs", daDungMs)
                .putLong("nqBatDauPhien", batDauPhien)
                .putLong("nqLucTat", lucTat)
                .apply();
    }

    public void nqDatKetThucNghi(long luc) {
        p.edit().putLong("nqKetThucNghi", luc).apply();
    }

    /* --- thống kê trong ngày --- */

    private String homNay() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Sang ngày mới thì xoá sạch số liệu hôm qua. */
    private void doiNgayNeuCan() {
        String nay = homNay();
        if (!nay.equals(p.getString("nqNgay", ""))) {
            p.edit().putString("nqNgay", nay)
                    .putLong("nqTongHomNay", 0L)
                    .putInt("nqSoDot", 0)
                    .putInt("nqSoKhanCap", 0)
                    .apply();
        }
    }

    public long nqTongHomNay()  { doiNgayNeuCan(); return p.getLong("nqTongHomNay", 0L); }
    public int nqSoDotHomNay()  { doiNgayNeuCan(); return p.getInt("nqSoDot", 0); }
    public int nqSoKhanCapHomNay() { doiNgayNeuCan(); return p.getInt("nqSoKhanCap", 0); }

    public void nqCongThemHomNay(long ms) {
        doiNgayNeuCan();
        p.edit().putLong("nqTongHomNay", p.getLong("nqTongHomNay", 0L) + ms).apply();
    }

    public void nqTangSoDot() {
        doiNgayNeuCan();
        p.edit().putInt("nqSoDot", p.getInt("nqSoDot", 0) + 1).apply();
    }

    public void nqTangSoKhanCap() {
        doiNgayNeuCan();
        p.edit().putInt("nqSoKhanCap", p.getInt("nqSoKhanCap", 0) + 1).apply();
    }

    public int nqSoNeTranhHomNay() { doiNgayNeuCan(); return p.getInt("nqSoNeTranh", 0); }

    /**
     * Đếm số lần né tránh trong ngày. Không chặn được người quyết tâm gỡ quyền,
     * nhưng mỗi lần đều để lại dấu — và con số này hiện ngay ở bảng tình trạng.
     */
    public void nqTangSoNeTranh() {
        doiNgayNeuCan();
        p.edit().putInt("nqSoNeTranh", p.getInt("nqSoNeTranh", 0) + 1).apply();
    }

    /* ==================== BĂM MÃ ==================== */

    public static String bam(String vanBan) {
        if (vanBan == null || vanBan.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(vanBan.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /* ==================== MỐC THỜI GIAN ==================== */

    /** Trả về mốc HH:mm của đúng cái ngày mà {@code goc} rơi vào. */
    public static long mocTrongNgay(long goc, String hhmm) {
        String[] pp = hhmm.split(":");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(goc);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(pp[0]));
        c.set(Calendar.MINUTE, Integer.parseInt(pp[1]));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Thời điểm {@code luc} có nằm trong khoảng {@code tu} -> {@code den} không.
     * Khoảng có thể vắt qua nửa đêm (22:45 -> 05:30). Hai mốc bằng nhau nghĩa
     * là áp dụng cả ngày.
     */
    public static boolean trongKhoang(long luc, String tu, String den) {
        long batDau = mocTrongNgay(luc, tu);
        long ketThuc = mocTrongNgay(luc, den);
        if (batDau == ketThuc) return true;
        if (ketThuc < batDau) return luc >= batDau || luc < ketThuc;
        return luc >= batDau && luc < ketThuc;
    }

    public boolean trongKhoangKhoa(long luc) {
        long batDau = mocTrongNgay(luc, gioKhoa());
        long ketThuc = mocTrongNgay(luc, gioKetThuc());
        if (ketThuc <= batDau) return luc >= batDau || luc < ketThuc;
        return luc >= batDau && luc < ketThuc;
    }

    /** Khung giờ áp dụng ngắt quãng. Từ = đến nghĩa là cả ngày. */
    public boolean nqTrongKhungGio(long luc) {
        return trongKhoang(luc, nqTuGio(), nqDenGio());
    }

    /** Lần khoá đầu tiên của đêm kế tiếp, tính từ {@code tu}. */
    public long mocKhoaDauTien(long tu) {
        long batDau = mocTrongNgay(tu, gioKhoa());
        if (tu < batDau) return batDau;
        return mocTrongNgay(tu + MOT_NGAY, gioKhoa());
    }

    /** Mốc khoá kế tiếp sau khi vừa mở khoá xong. */
    public long mocSauKhiMo(long bayGio) {
        long tiep = bayGio + lapLaiPhut() * 60_000L;
        if (!trongKhoangKhoa(tiep)) tiep = mocKhoaDauTien(tiep);
        return tiep;
    }

    /** Danh sách mốc cảnh báo, đã sắp giảm dần và bỏ giá trị vô lý. */
    public List<Integer> mocCanhBao() {
        return tachSo(canhBaoPhut());
    }

    /** Tách chuỗi "15, 5, 1" thành [15, 5, 1]. Bỏ qua phần không phải số. */
    public static List<Integer> tachSo(String chuoi) {
        List<Integer> ra = new ArrayList<>();
        if (chuoi == null) return ra;
        for (String phan : chuoi.split(",")) {
            String s = phan.trim();
            if (s.isEmpty()) continue;
            try {
                int v = Integer.parseInt(s);
                if (v > 0 && !ra.contains(v)) ra.add(v);
            } catch (NumberFormatException ignore) {
                // bỏ qua phần nhập sai
            }
        }
        ra.sort((a, b) -> b - a);
        return ra;
    }

    /** Kiểm tra chuỗi có đúng dạng HH:mm không. */
    public static boolean laGio(String s) {
        if (s == null) return false;
        return s.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    }

    /** "1 giờ 23 phút" cho phần thống kê. */
    public static String doDai(long ms) {
        long phut = ms / 60_000L;
        if (phut < 60) return phut + " phút";
        return (phut / 60) + " giờ " + (phut % 60) + " phút";
    }
}
