package com.thoigianbieu.kiemsoat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Đặt báo thức cho lần khoá kế tiếp và cho từng mốc cảnh báo trước đó.
 * Dùng báo thức chính xác để Doze không hoãn mất.
 */
public class LenLich {

    public static final String VIEC = "viec";
    public static final String VIEC_KHOA = "khoa";
    public static final String VIEC_CANH_BAO = "canh_bao";
    public static final String VIEC_NHIP = "nhip";
    public static final String SO_PHUT = "so_phut";

    private static final int MA_KHOA = 1000;
    private static final int MA_CANH_BAO = 2000;   // + số phút
    private static final int MA_NHIP = 3000;

    /**
     * Nhịp tự hồi phục, cũng là cái phao dựng lại dịch vụ.
     *
     * Trước để 2 tiếng cho nhẹ pin, nhưng user yêu cầu app phải LUÔN chạy nền.
     * Báo thức mới là thứ duy nhất sống sót qua việc tiến trình bị giết — dịch
     * vụ chết thì chính nhịp này dựng lại. Nửa tiếng một lần, và dùng báo thức
     * KHÔNG chính xác nên Android gộp chung với các lần đánh thức sẵn có của hệ
     * thống, gần như không tốn thêm pin.
     */
    private static final long CHU_KY_NHIP = 30L * 60 * 1000;

    /** Tính lại mốc khoá kế tiếp nếu cần rồi đặt toàn bộ báo thức. */
    public static void datLai(Context ctx) {
        CauHinh ch = new CauHinh(ctx);
        huyHet(ctx, ch);
        if (!ch.daDatMa()) return;

        long bayGio = System.currentTimeMillis();
        long moc = ch.mocKhoa();
        if (moc <= bayGio) {
            moc = ch.trongKhoangKhoa(bayGio) ? bayGio + 60_000L : ch.mocKhoaDauTien(bayGio);
            ch.datMocKhoa(moc);
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        dat(ctx, am, MA_KHOA, moc, VIEC_KHOA, 0);

        // Nhớ lại đúng những mốc đã đặt, để lần sau huỷ cho sạch.
        java.util.List<Integer> daDat = new java.util.ArrayList<>();
        for (int phut : ch.mocCanhBao()) {
            long luc = moc - phut * 60_000L;
            if (luc > bayGio) {
                dat(ctx, am, MA_CANH_BAO + phut, luc, VIEC_CANH_BAO, phut);
                daDat.add(phut);
            }
        }
        ch.luuMocCanhBaoDaDat(daDat);
        datNhip(ctx, am, bayGio);

        NhatKy.ghi(ctx, "len-lich", "khoá lúc " + gioPhut(moc));
    }

    /**
     * Báo thức lặp cứ nửa tiếng một lần. Không phải để khoá, mà để dựng lại
     * dịch vụ và soát lại lịch nếu hệ thống đã dọn mất — app tắt vì bất cứ lý
     * do gì thì chậm nhất nửa tiếng sau tự sống lại.
     */
    private static void datNhip(Context ctx, AlarmManager am, long bayGio) {
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                bayGio + CHU_KY_NHIP, CHU_KY_NHIP,
                taoY(ctx, MA_NHIP, VIEC_NHIP, 0));
    }

    private static void dat(Context ctx, AlarmManager am, int ma, long luc,
                            String viec, int soPhut) {
        PendingIntent pi = taoY(ctx, ma, viec, soPhut);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, luc, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, luc, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, luc, pi);
        }
    }

    private static void huyHet(Context ctx, CauHinh ch) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(taoY(ctx, MA_KHOA, VIEC_KHOA, 0));
        // Huỷ đúng những mốc đã đặt lần trước. Quét mù một dải cố định sẽ bỏ
        // sót mốc lớn hơn dải đó và để lại báo thức ma nổ ra cảnh báo vô nghĩa
        // sau khi người dùng sửa danh sách.
        for (int phut : ch.mocCanhBaoDaDat()) {
            am.cancel(taoY(ctx, MA_CANH_BAO + phut, VIEC_CANH_BAO, phut));
        }
    }

    private static PendingIntent taoY(Context ctx, int ma, String viec, int soPhut) {
        Intent i = new Intent(ctx, BaoThucReceiver.class);
        i.setAction("tgb.bao_thuc." + ma);
        i.putExtra(VIEC, viec);
        i.putExtra(SO_PHUT, soPhut);
        return PendingIntent.getBroadcast(ctx, ma, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static String gioPhut(long luc) {
        return new SimpleDateFormat("HH:mm dd/MM", Locale.US).format(new Date(luc));
    }
}
