package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Cấu hình và toàn bộ phép tính mốc thời gian. Không đụng tới giao diện,
 * nhờ vậy kiểm thử được độc lập.
 */
public class CauHinh {

    private static final String TEP = "kiem_soat_may";
    public static final int DO_DAI_MA_TOI_THIEU = 10;
    private static final long MOT_NGAY = 24L * 60 * 60 * 1000;

    private final SharedPreferences p;

    public CauHinh(Context ctx) {
        p = ctx.getApplicationContext().getSharedPreferences(TEP, Context.MODE_PRIVATE);
    }

    /* ---------- đọc ---------- */

    public String gioKhoa()      { return p.getString("gioKhoa", "22:45"); }
    public String gioKetThuc()   { return p.getString("gioKetThuc", "05:30"); }
    public int lapLaiPhut()      { return p.getInt("lapLaiPhut", 15); }
    public int khoangCachGiay()  { return p.getInt("khoangCachGiay", 15); }
    public String canhBaoPhut()  { return p.getString("canhBaoPhut", "15,5,1"); }
    public String maBam()        { return p.getString("maBam", ""); }
    public boolean daDatMa()     { return !maBam().isEmpty(); }
    public long mocKhoa()        { return p.getLong("mocKhoa", 0L); }

    /* ---------- ghi ---------- */

    public void luu(String gioKhoa, String gioKetThuc, int lapLaiPhut,
                    String canhBaoPhut, int khoangCachGiay) {
        p.edit()
                .putString("gioKhoa", gioKhoa)
                .putString("gioKetThuc", gioKetThuc)
                .putInt("lapLaiPhut", lapLaiPhut)
                .putString("canhBaoPhut", canhBaoPhut)
                .putInt("khoangCachGiay", khoangCachGiay)
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

    /* ---------- băm ---------- */

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

    /* ---------- mốc thời gian ---------- */

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
     * Thời điểm này có nằm trong khoảng "giờ khoá -> giờ kết thúc" không.
     * Khoảng này thường vắt qua nửa đêm, ví dụ 22:45 -> 05:30.
     */
    public boolean trongKhoangKhoa(long luc) {
        long batDau = mocTrongNgay(luc, gioKhoa());
        long ketThuc = mocTrongNgay(luc, gioKetThuc());
        if (ketThuc <= batDau) {
            return luc >= batDau || luc < ketThuc;
        }
        return luc >= batDau && luc < ketThuc;
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
        List<Integer> ra = new ArrayList<>();
        for (String phan : canhBaoPhut().split(",")) {
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
}
