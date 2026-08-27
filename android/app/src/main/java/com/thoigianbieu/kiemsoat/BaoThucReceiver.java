package com.thoigianbieu.kiemsoat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Báo thức nổ: chuyển việc sang dịch vụ nền, vì chỉ dịch vụ mới dựng được lớp phủ. */
public class BaoThucReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String viec = intent.getStringExtra(LenLich.VIEC);
        int soPhut = intent.getIntExtra(LenLich.SO_PHUT, 0);
        if (viec == null) return;

        String hanhDong;
        if (LenLich.VIEC_CANH_BAO.equals(viec)) {
            hanhDong = DichVuKhoa.HANH_DONG_CANH_BAO;
        } else if (LenLich.VIEC_NHIP.equals(viec)) {
            hanhDong = DichVuKhoa.HANH_DONG_CANH_GIU;
        } else {
            hanhDong = DichVuKhoa.HANH_DONG_KHOA;
        }

        Intent i = new Intent(ctx, DichVuKhoa.class);
        i.setAction(hanhDong);
        i.putExtra(LenLich.SO_PHUT, soPhut);
        ctx.startForegroundService(i);
    }
}
