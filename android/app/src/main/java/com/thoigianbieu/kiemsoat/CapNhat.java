package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tự tìm và cài bản mới, khỏi phải chép tay APK sang điện thoại mỗi lần.
 *
 * Vì sao không im lặng cài luôn: Android không cho app nào tự cài đè chính nó
 * mà không hỏi, trừ khi máy đã root hoặc app là chủ sở hữu thiết bị. Nên chỗ
 * này làm được tới mức "một chạm": app tự phát hiện bản mới, tự tải, rồi mở
 * thẳng trình cài đặt của hệ thống — người dùng chỉ bấm Cài đặt một cái.
 */
public class CapNhat {

    /** Bảng phiên bản nằm ngay cạnh APK trong repo. */
    private static final String DIA_CHI_JSON =
            "https://raw.githubusercontent.com/hovidu86/ThoiGianBieu/main/android/version.json";

    private static final String TEP = "cap_nhat";
    private static final String TEN_APK = "ThoiGianBieu-moi.apk";
    /** Tự kiểm tra nhiều nhất 6 tiếng một lần, đừng quấy mạng vô ích. */
    private static final long GIAN_CACH = 6L * 60 * 60 * 1000;

    private static final Handler TAY = new Handler(Looper.getMainLooper());

    public static class BanMoi {
        public int maPhienBan;
        public String tenPhienBan = "";
        public String diaChiApk = "";
        public String ghiChu = "";
    }

    public interface Xong {
        /** {@code banMoi} là null nghĩa là đang dùng bản mới nhất rồi. */
        void xong(BanMoi banMoi);
        void hong(String loi);
    }

    public interface TienDo {
        void phanTram(int p);
    }

    public static int maPhienBanHienTai(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return (int) p.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String tenPhienBanHienTai(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Đã tới lúc tự kiểm tra chưa. Bấm nút kiểm tra tay thì bỏ qua hàm này. */
    public static boolean toiLucTuKiemTra(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(TEP, Context.MODE_PRIVATE);
        return System.currentTimeMillis() - p.getLong("lanCuoi", 0) > GIAN_CACH;
    }

    private static void ghiNhoDaKiemTra(Context ctx) {
        ctx.getSharedPreferences(TEP, Context.MODE_PRIVATE)
                .edit().putLong("lanCuoi", System.currentTimeMillis()).apply();
    }

    /* ==================== KIỂM TRA ==================== */

    public static void kiemTra(final Context ctx, final Xong xong) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String noi = tai(DIA_CHI_JSON);
                JSONObject o = new JSONObject(noi);
                final BanMoi b = new BanMoi();
                b.maPhienBan = o.optInt("versionCode", 0);
                b.tenPhienBan = o.optString("versionName", "");
                b.diaChiApk = o.optString("apk", "");
                b.ghiChu = o.optString("ghiChu", "");
                ghiNhoDaKiemTra(app);

                final boolean coMoi = b.maPhienBan > maPhienBanHienTai(app)
                        && !b.diaChiApk.isEmpty();
                TAY.post(() -> xong.xong(coMoi ? b : null));
            } catch (final Exception e) {
                final String loi = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                TAY.post(() -> xong.hong(loi));
            }
        }).start();
    }

    /* ==================== TẢI VÀ CÀI ==================== */

    public static void taiVaCai(final Context ctx, final BanMoi b,
                                final TienDo tienDo, final Xong xong) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                File dich = new File(NhaCungCapApk.thuMuc(app), TEN_APK);
                taiTep(b.diaChiApk, dich, tienDo);
                TAY.post(() -> {
                    moTrinhCaiDat(ctx, dich);
                    xong.xong(b);
                });
            } catch (final Exception e) {
                final String loi = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                TAY.post(() -> xong.hong(loi));
            }
        }).start();
    }

    private static void moTrinhCaiDat(Context ctx, File apk) {
        Uri uri = NhaCungCapApk.uriCho(apk.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    /* ==================== MẠNG ==================== */

    private static String tai(String diaChi) throws Exception {
        HttpURLConnection kn = (HttpURLConnection) new URL(diaChi).openConnection();
        try {
            kn.setConnectTimeout(15000);
            kn.setReadTimeout(15000);
            int ma = kn.getResponseCode();
            if (ma < 200 || ma >= 300) throw new Exception("HTTP " + ma);
            InputStream in = kn.getInputStream();
            java.io.ByteArrayOutputStream ra = new java.io.ByteArrayOutputStream();
            byte[] dem = new byte[8192];
            int n;
            while ((n = in.read(dem)) > 0) ra.write(dem, 0, n);
            in.close();
            String noi = ra.toString("UTF-8");
            // Cắt BOM nếu có: vài công cụ trên Windows chèn nó vào đầu tệp và
            // bộ đọc JSON sẽ ném lỗi ngay ký tự đầu tiên.
            if (!noi.isEmpty() && noi.charAt(0) == '﻿') noi = noi.substring(1);
            return noi;
        } finally {
            kn.disconnect();
        }
    }

    private static void taiTep(String diaChi, File dich, TienDo tienDo) throws Exception {
        HttpURLConnection kn = (HttpURLConnection) new URL(diaChi).openConnection();
        try {
            kn.setConnectTimeout(20000);
            kn.setReadTimeout(60000);
            kn.setInstanceFollowRedirects(true);
            int ma = kn.getResponseCode();
            if (ma < 200 || ma >= 300) throw new Exception("HTTP " + ma);

            final int tong = kn.getContentLength();
            InputStream in = kn.getInputStream();
            FileOutputStream ra = new FileOutputStream(dich);
            byte[] dem = new byte[16384];
            int n, daTai = 0, phanTramCu = -1;
            while ((n = in.read(dem)) > 0) {
                ra.write(dem, 0, n);
                daTai += n;
                if (tong > 0 && tienDo != null) {
                    final int p = (int) (daTai * 100L / tong);
                    if (p != phanTramCu) {
                        phanTramCu = p;
                        TAY.post(() -> tienDo.phanTram(p));
                    }
                }
            }
            ra.close();
            in.close();
            if (dich.length() < 10_000) throw new Exception("Tệp tải về hỏng hoặc quá nhỏ");
        } finally {
            kn.disconnect();
        }
    }
}
