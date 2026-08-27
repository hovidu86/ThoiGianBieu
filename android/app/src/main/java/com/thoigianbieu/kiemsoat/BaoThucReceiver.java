package com.thoigianbieu.kiemsoat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Báo thức nổ: chuyển việc sang dịch vụ nền, vì chỉ dịch vụ mới dựng được lớp phủ.
 *
 * Cẩn thận với startForegroundService: từ Android 12, app chạy nền chỉ được
 * dựng dịch vụ tiền cảnh trong vài trường hợp miễn trừ. Báo thức CHÍNH XÁC là
 * một trong số đó, nhưng nhịp tự hồi phục dùng báo thức KHÔNG chính xác thì
 * không — mà đó lại đúng là lúc dịch vụ đã chết và cần dựng lại nhất. Nên nhịp
 * tự làm việc của nó ngay tại đây, và mọi lời gọi dịch vụ đều phải bọc lại.
 */
public class BaoThucReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String viec = intent.getStringExtra(LenLich.VIEC);
        int soPhut = intent.getIntExtra(LenLich.SO_PHUT, 0);
        if (viec == null) return;

        // Nhịp tự hồi phục: dựng lại lịch ngay trong receiver, không phụ thuộc
        // vào việc có dựng nổi dịch vụ hay không.
        if (LenLich.VIEC_NHIP.equals(viec)) {
            LenLich.datLai(ctx);
            goiDichVu(ctx, DichVuKhoa.HANH_DONG_CANH_GIU, 0, false);
            return;
        }

        boolean laViecKhoa = !LenLich.VIEC_CANH_BAO.equals(viec);
        goiDichVu(ctx,
                laViecKhoa ? DichVuKhoa.HANH_DONG_KHOA : DichVuKhoa.HANH_DONG_CANH_BAO,
                soPhut, laViecKhoa);
    }

    /**
     * @param khoaBuNeuHong dựng dịch vụ không được thì ít nhất cũng tắt màn hình,
     *                      thà mất phần đòi mã còn hơn đêm đó không khoá gì cả.
     */
    private void goiDichVu(Context ctx, String hanhDong, int soPhut, boolean khoaBuNeuHong) {
        Intent i = new Intent(ctx, DichVuKhoa.class);
        i.setAction(hanhDong);
        i.putExtra(LenLich.SO_PHUT, soPhut);
        try {
            ctx.startForegroundService(i);
        } catch (Exception e) {
            NhatKy.ghi(ctx, "loi-dung-dich-vu", hanhDong + ": " + e.getMessage());
            if (khoaBuNeuHong) {
                QuanTriReceiver.khoaNgay(ctx);
                CauHinh ch = new CauHinh(ctx);
                ch.datMocKhoa(ch.mocSauKhiMo(System.currentTimeMillis()));
                LenLich.datLai(ctx);
            }
        }
    }
}
