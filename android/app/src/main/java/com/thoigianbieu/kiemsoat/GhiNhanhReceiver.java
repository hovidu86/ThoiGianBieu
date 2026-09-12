package com.thoigianbieu.kiemsoat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Nút "Đúng, ghi 22:45" trên lời nhắc buổi sáng: ghi đêm đó với giờ gợi ý mà
 * không cần mở app, rồi thay lời nhắc bằng một dòng kết quả (tiền, chuỗi).
 *
 * Dùng đúng {@link KhoGiacNgu#ghiDem} và {@link DongBo#day} như nút Xác nhận
 * trong ChinhActivity — không có luật riêng nào ở đây. Kho là singleton dùng
 * chung với Activity nên Activity đang mở nền cũng thấy đêm mới khi quay lại.
 */
public class GhiNhanhReceiver extends BroadcastReceiver {

    public static final String VIEC = "com.thoigianbieu.kiemsoat.GHI_NHANH";
    public static final String DEM = "dem";
    public static final String GIO = "gio";

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        final String dem = intent.getStringExtra(DEM);
        final String gio = GiaoDien.chuanHoaGio(intent.getStringExtra(GIO));
        if (dem == null || !dem.matches("^\\d{4}-\\d{2}-\\d{2}$") || !CauHinh.laGio(gio)) {
            NhatKy.ghi(ctx, "loi-ghi-nhanh", "thiếu đêm/giờ: " + dem + " " + gio);
            return;
        }

        final KhoGiacNgu kho = KhoGiacNgu.cua(ctx);
        kho.doiDemNeuCan();

        // Ai bấm nút hai lần (thông báo chưa kịp biến) thì lần hai bỏ qua,
        // không ghi đè đêm vừa ghi.
        if (kho.timTheoNgay(dem) != null) {
            NhatKy.ghi(ctx, "ghi-nhanh", "đêm " + dem + " đã có, bỏ qua");
            NhacNgu.huyNhacSang(ctx);
            return;
        }

        // Số bước đã tích chỉ tính cho đúng đêm nay — y hệt ChinhActivity.ghiThat.
        int soBuoc = dem.equals(kho.demNay) ? kho.daTich.size() : 0;
        DemNgu d = kho.ghiDem(dem, gio, "", soBuoc);
        NhatKy.ghi(ctx, "ghi-nhanh", "đêm " + dem + " lúc " + gio + " từ thông báo");

        String ketQua = d == null ? "Đã ghi." : (d.tongTien >= 0 ? "Đã ghi. " : "Đã ghi, bị phạt. ")
                + LuatGiacNgu.tienCoDau(d.tongTien) + " · chuỗi " + d.chuoiSauDem + " ngày.";
        baoKetQua(ctx, "Đêm " + LuatGiacNgu.ngayNgan(dem) + ": lên giường " + gio, ketQua);

        // Đẩy lên Sheet như nút Xác nhận. Không có URL thì để đó, lần mở app
        // sau sẽ đẩy bù (ChinhActivity.dayNhungCaiChuaGui).
        final List<DemNgu> can = kho.chuaGui();
        if (can.isEmpty() || !DongBo.urlHopLe(kho.caiDat.urlWebApp)) return;
        final PendingResult cho = goAsync();
        DongBo.day(kho.caiDat.urlWebApp, can, new DongBo.Xong<Integer>() {
            @Override
            public void thanhCong(Integer soDaLuu) {
                for (DemNgu x : can) { x.daGui = true; x.daXacNhan = true; }
                kho.luuDanhSach();
                NhatKy.ghi(ctx, "ghi-nhanh", "đã đẩy " + can.size() + " đêm lên Sheet");
                cho.finish();
            }

            @Override
            public void thatBai(String loi) {
                NhatKy.ghi(ctx, "loi-gui", "ghi nhanh: " + loi);
                cho.finish();
            }
        });
    }

    /** Thay lời nhắc bằng dòng kết quả; bấm vào mở tab Ghi nhận để xem/sửa. */
    private static void baoKetQua(Context ctx, String tieuDe, String noiDung) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Intent moApp = new Intent(ctx, ChinhActivity.class);
        moApp.putExtra(ChinhActivity.MO_TAB, ChinhActivity.TAB_GHI_NHAN);
        PendingIntent pi = PendingIntent.getActivity(ctx, NhacNgu.ID_TB_SANG, moApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NhacNgu.ID_TB_SANG, new Notification.Builder(ctx, NhacNgu.KENH)
                .setContentTitle(tieuDe)
                .setContentText(noiDung)
                .setStyle(new Notification.BigTextStyle().bigText(noiDung))
                .setSmallIcon(R.drawable.bieu_tuong)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }
}
