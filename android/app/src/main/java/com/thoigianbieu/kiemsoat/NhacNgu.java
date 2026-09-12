package com.thoigianbieu.kiemsoat;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Ba lời nhắc của module Giấc ngủ:
 *   - Tới giờ chuẩn bị ngủ (mặc định 21:30).
 *   - Tới giờ mục tiêu lên giường (mặc định 22:30), quá là bắt đầu bị phạt.
 *   - Sáng hôm sau (mặc định 08:00): ghi giờ lên giường đêm qua, kèm gợi ý
 *     lấy từ sổ phiên dùng máy ({@link PhienDung}) và nút ghi một chạm.
 *
 * Đêm nào đã ghi nhận rồi thì im lặng, không nhắc nữa.
 */
public class NhacNgu {

    public static final String KENH = "nhac_ngu";
    public static final String VIEC = "viec_nhac";
    public static final String VIEC_CHUAN_BI = "chuan_bi";
    public static final String VIEC_LEN_GIUONG = "len_giuong";
    public static final String VIEC_SANG = "sang";

    private static final int MA_CHUAN_BI = 5000;
    private static final int MA_LEN_GIUONG = 5001;
    private static final int MA_SANG = 5002;
    private static final int ID_TB_CHUAN_BI = 11;
    private static final int ID_TB_LEN_GIUONG = 12;
    public static final int ID_TB_SANG = 13;

    /** Đặt lại cả hai lời nhắc cho lần tới. Gọi sau mỗi lần sửa cài đặt. */
    public static void datLai(Context ctx) {
        KhoGiacNgu kho = KhoGiacNgu.cua(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long bayGio = System.currentTimeMillis();
        dat(ctx, am, MA_CHUAN_BI, VIEC_CHUAN_BI,
                LuatGiacNgu.mocKeTiep(kho.caiDat.nhacLuc, bayGio));
        dat(ctx, am, MA_LEN_GIUONG, VIEC_LEN_GIUONG,
                LuatGiacNgu.mocKeTiep(kho.caiDat.truocGioNay, bayGio));
        if (CauHinh.laGio(kho.caiDat.nhacSangLuc)) {
            dat(ctx, am, MA_SANG, VIEC_SANG,
                    LuatGiacNgu.mocKeTiep(kho.caiDat.nhacSangLuc, bayGio));
        } else {
            am.cancel(taoY(ctx, MA_SANG, VIEC_SANG));
        }
    }

    private static void dat(Context ctx, AlarmManager am, int ma, String viec, long luc) {
        PendingIntent pi = taoY(ctx, ma, viec);
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

    private static PendingIntent taoY(Context ctx, int ma, String viec) {
        Intent i = new Intent(ctx, NhacNguReceiver.class);
        i.setAction("tgb.nhac." + ma);
        i.putExtra(VIEC, viec);
        return PendingIntent.getBroadcast(ctx, ma, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Lời nhắc nổ. Trả về false nếu bỏ qua vì đêm nay đã ghi rồi. */
    public static boolean nhac(Context ctx, String viec) {
        KhoGiacNgu kho = KhoGiacNgu.cua(ctx);
        kho.doiDemNeuCan();

        if (kho.timTheoNgay(kho.demNay) != null) {
            NhatKy.ghi(ctx, "bo-nhac", "đêm " + kho.demNay + " đã ghi rồi");
            return false;
        }
        if (VIEC_SANG.equals(viec)) return nhacSang(ctx, kho);

        String tieuDe, noiDung;
        int id;
        if (VIEC_LEN_GIUONG.equals(viec)) {
            id = ID_TB_LEN_GIUONG;
            tieuDe = "Lên giường ngay!";
            noiDung = "Quá " + kho.caiDat.truocGioNay + " là bắt đầu bị phạt tiền. "
                    + "Chuỗi hiện tại " + kho.chuoiHienTai + " ngày.";
        } else {
            id = ID_TB_CHUAN_BI;
            int soBuoc = kho.caiDat.cacBuoc.size();
            tieuDe = "Tới giờ chuẩn bị ngủ";
            noiDung = "Làm đủ " + soBuoc + " bước chuẩn bị rồi lên giường trước "
                    + kho.caiDat.truocGioNay + " để được thưởng "
                    + LuatGiacNgu.tien(kho.caiDat.thuongThoiQuen) + ".";
        }

        taoKenh(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return false;

        Intent moApp = new Intent(ctx, ChinhActivity.class);
        moApp.putExtra(ChinhActivity.MO_TAB, ChinhActivity.TAB_GHI_NHAN);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, moApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent ghiNgay = new Intent(ctx, ChinhActivity.class);
        ghiNgay.putExtra(ChinhActivity.MO_TAB, ChinhActivity.TAB_GHI_NHAN);
        ghiNgay.putExtra(ChinhActivity.GHI_NGAY, true);
        PendingIntent piGhi = PendingIntent.getActivity(ctx, id + 100, ghiNgay,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification tb = new Notification.Builder(ctx, KENH)
                .setContentTitle(tieuDe)
                .setContentText(noiDung)
                .setStyle(new Notification.BigTextStyle().bigText(noiDung))
                .setSmallIcon(R.drawable.bieu_tuong)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        null, "Ghi ngay", piGhi).build())
                .build();
        nm.notify(id, tb);

        NhatKy.ghi(ctx, "nhac-ngu", viec);
        return true;
    }

    /**
     * Lời nhắc buổi sáng. Trước 18:00 thì "đêm nay" của kho chính là đêm hôm
     * qua (xem LuatGiacNgu.demHienTai), nên {@code kho.demNay} đúng là đêm
     * cần ghi. Gợi ý = đầu quãng không dùng máy dài nhất kể từ 18:00 đêm đó.
     */
    private static boolean nhacSang(Context ctx, KhoGiacNgu kho) {
        long bayGio = System.currentTimeMillis();
        String dem = kho.demNay;
        String goiY = goiYGioLenGiuong(ctx, dem, bayGio);

        String tieuDe, noiDung;
        if (goiY != null) {
            tieuDe = "Tối qua lên giường lúc ~" + goiY + "?";
            noiDung = "Theo lần buông máy cuối cùng. Bấm \"Đúng\" để ghi luôn, "
                    + "hoặc bấm vào đây để sửa trước khi ghi đêm "
                    + LuatGiacNgu.ngayNgan(dem) + ".";
        } else {
            tieuDe = "Ghi giờ lên giường tối qua";
            noiDung = "Chưa đủ dữ liệu dùng máy để gợi ý. Bấm vào đây để điền nhanh đêm "
                    + LuatGiacNgu.ngayNgan(dem) + ".";
        }

        taoKenh(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return false;

        Intent moHop = new Intent(ctx, ChinhActivity.class);
        moHop.setAction(ChinhActivity.VIEC_GHI_NHANH);
        moHop.putExtra(ChinhActivity.MO_TAB, ChinhActivity.TAB_GHI_NHAN);
        moHop.putExtra(ChinhActivity.GHI_NHANH_DEM, dem);
        if (goiY != null) moHop.putExtra(ChinhActivity.GHI_NHANH_GIO, goiY);
        PendingIntent piHop = PendingIntent.getActivity(ctx, ID_TB_SANG, moHop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(ctx, KENH)
                .setContentTitle(tieuDe)
                .setContentText(noiDung)
                .setStyle(new Notification.BigTextStyle().bigText(noiDung))
                .setSmallIcon(R.drawable.bieu_tuong)
                .setContentIntent(piHop)
                .setAutoCancel(true);
        if (goiY != null) {
            Intent ghi = new Intent(ctx, GhiNhanhReceiver.class);
            ghi.setAction(GhiNhanhReceiver.VIEC);
            ghi.putExtra(GhiNhanhReceiver.DEM, dem);
            ghi.putExtra(GhiNhanhReceiver.GIO, goiY);
            PendingIntent piGhi = PendingIntent.getBroadcast(ctx, ID_TB_SANG + 100, ghi,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null, "Đúng, ghi " + goiY, piGhi).build());
        }
        nm.notify(ID_TB_SANG, b.build());

        NhatKy.ghi(ctx, "nhac-sang", "đêm " + dem + ", gợi ý " + goiY);
        return true;
    }

    /**
     * Giờ lên giường gợi ý cho {@code dem}, dạng "HH:mm" đã làm tròn 5 phút,
     * hoặc null nếu sổ phiên dùng máy không có quãng nghỉ nào đáng kể.
     */
    public static String goiYGioLenGiuong(Context ctx, String dem, long bayGio) {
        PhienDung pd = new PhienDung(ctx);
        long dangMo = pd.dangMoTu();
        long[] q = PhienDung.quangNghiDaiNhat(pd.danhSach(), PhienDung.dauDem(dem), bayGio,
                dangMo > 0 ? dangMo : bayGio);
        return q == null ? null : PhienDung.gioTron5(q[0]);
    }

    public static void huyNhacSang(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID_TB_SANG);
    }

    private static void taoKenh(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel k = new NotificationChannel(
                KENH, "Nhắc giờ ngủ", NotificationManager.IMPORTANCE_HIGH);
        k.enableVibration(true);
        nm.createNotificationChannel(k);
    }
}
