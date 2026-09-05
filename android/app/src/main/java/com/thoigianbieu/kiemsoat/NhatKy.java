package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Ghi lại mọi lần khoá, mở khoá, nhập sai, né tránh và mỗi đợt ngắt quãng. */
public class NhatKy {

    private static final String TEN_TEP = "nhat-ky-may.csv";
    /** Giữ lại chừng này dòng gần nhất, cắt bớt phần cũ cho khỏi phình vô hạn. */
    private static final int SO_DONG_GIU = 400;

    public static void ghi(Context ctx, String suKien, String chiTiet) {
        Log.i("KiemSoatMay", suKien + " - " + chiTiet);
        try {
            File f = tep(ctx);
            boolean moi = !f.exists();
            FileWriter w = new FileWriter(f, true);
            if (moi) w.write("thoi_diem,su_kien,chi_tiet\n");
            String luc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            w.write(luc + "," + suKien + ",\"" + chiTiet.replace('"', '\'') + "\"\n");
            w.close();

            // Kiểm thưa thôi, mỗi lần ghi mà đọc lại cả tệp thì phí.
            if (f.length() > 120_000L) catBotPhanCu(f);
        } catch (Exception e) {
            Log.w("KiemSoatMay", "khong ghi duoc nhat ky", e);
        }
    }

    private static File tep(Context ctx) {
        return new File(ctx.getApplicationContext().getFilesDir(), TEN_TEP);
    }

    /** Đọc toàn bộ nhật ký. Trả về chuỗi rỗng nếu chưa có gì. */
    public static String doc(Context ctx) {
        File f = tep(ctx);
        if (!f.exists()) return "";
        StringBuilder sb = new StringBuilder();
        // Đọc theo dòng: một lần read() vào mảng byte không bảo đảm lấy hết tệp,
        // và khi nhật ký dài ra thì hộp thoại sẽ lặng lẽ hiện thiếu.
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String dong;
            while ((dong = r.readLine()) != null) {
                sb.append(dong).append('\n');
            }
        } catch (Exception e) {
            Log.w("KiemSoatMay", "khong doc duoc nhat ky", e);
        }
        return sb.toString();
    }

    /** Đọc ngược: những dòng mới nhất nằm trên cùng, tiện xem trong hộp thoại. */
    public static String docMoiNhatTruoc(Context ctx, int soDong) {
        String[] dong = doc(ctx).split("\n");
        StringBuilder sb = new StringBuilder();
        int dem = 0;
        for (int i = dong.length - 1; i >= 1 && dem < soDong; i--) {   // bỏ dòng tiêu đề
            if (dong[i].trim().isEmpty()) continue;
            sb.append(dong[i]).append('\n');
            dem++;
        }
        return sb.toString();
    }

    /**
     * Như {@link #docMoiNhatTruoc}, nhưng dựng lại cho dễ nhìn trong hộp
     * thoại thay vì phô hết dấu phẩy/ngoặc kép của CSV thô: giờ:phút:giây
     * và tên sự kiện trên một dòng, chi tiết thụt vào dòng dưới, cách nhau
     * một dòng trống; chỉ chêm vạch ngày khi ngày đổi (đa số lúc mở ra toàn
     * bộ nhật ký hiện đều cùng một ngày, lặp lại ngày mỗi dòng chỉ tổ rối).
     */
    public static String docDeXem(Context ctx, int soDong) {
        String tho = docMoiNhatTruoc(ctx, soDong);
        if (tho.isEmpty()) return "";

        StringBuilder ra = new StringBuilder();
        String ngayTruoc = null;
        for (String dong : tho.split("\n")) {
            if (dong.trim().isEmpty()) continue;

            int i1 = dong.indexOf(',');
            int i2 = i1 < 0 ? -1 : dong.indexOf(',', i1 + 1);
            if (i1 < 0 || i2 < 0) {
                ra.append(dong).append("\n\n");
                continue;
            }
            String thoiDiem = dong.substring(0, i1);
            String suKien = dong.substring(i1 + 1, i2);
            String chiTiet = dong.substring(i2 + 1).trim();
            if (chiTiet.length() >= 2 && chiTiet.startsWith("\"") && chiTiet.endsWith("\"")) {
                chiTiet = chiTiet.substring(1, chiTiet.length() - 1);
            }

            String ngay = thoiDiem.length() >= 10 ? thoiDiem.substring(0, 10) : "";
            String gio = thoiDiem.length() >= 19 ? thoiDiem.substring(11) : thoiDiem;
            if (!ngay.isEmpty() && !ngay.equals(ngayTruoc)) {
                if (ngayTruoc != null) ra.append('\n');
                ra.append("── ").append(ngay).append(" ──\n");
                ngayTruoc = ngay;
            }

            ra.append(gio).append("  ").append(suKien).append('\n');
            ra.append("    ").append(chiTiet).append("\n\n");
        }
        return ra.toString().trim();
    }

    private static void catBotPhanCu(File f) {
        try {
            List<String> giu = new ArrayList<>();
            String tieuDe = null;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                String dong;
                while ((dong = r.readLine()) != null) {
                    if (tieuDe == null) { tieuDe = dong; continue; }
                    giu.add(dong);
                    if (giu.size() > SO_DONG_GIU * 2) {
                        giu.subList(0, giu.size() - SO_DONG_GIU).clear();
                    }
                }
            }
            if (giu.size() > SO_DONG_GIU) {
                giu.subList(0, giu.size() - SO_DONG_GIU).clear();
            }
            FileWriter w = new FileWriter(f, false);
            w.write((tieuDe == null ? "thoi_diem,su_kien,chi_tiet" : tieuDe) + "\n");
            for (String d : giu) w.write(d + "\n");
            w.close();
        } catch (Exception e) {
            Log.w("KiemSoatMay", "khong cat bot duoc nhat ky", e);
        }
    }
}
