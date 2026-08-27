package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ghi lại mọi lần khoá, mở khoá, nhập sai và né tránh. */
public class NhatKy {

    private static final String TEN_TEP = "nhat-ky-may.csv";

    public static void ghi(Context ctx, String suKien, String chiTiet) {
        Log.i("KiemSoatMay", suKien + " - " + chiTiet);
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), TEN_TEP);
            boolean moi = !f.exists();
            FileWriter w = new FileWriter(f, true);
            if (moi) w.write("thoi_diem,su_kien,chi_tiet\n");
            String luc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            w.write(luc + "," + suKien + ",\"" + chiTiet.replace('"', '\'') + "\"\n");
            w.close();
        } catch (Exception e) {
            Log.w("KiemSoatMay", "khong ghi duoc nhat ky", e);
        }
    }

    public static String doc(Context ctx) {
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), TEN_TEP);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int doc = in.read(b);
            in.close();
            return doc > 0 ? new String(b, 0, doc, "UTF-8") : "";
        } catch (Exception e) {
            return "";
        }
    }
}
