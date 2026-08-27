package com.thoigianbieu.kiemsoat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Khởi động lại máy hoặc cập nhật app xong thì dựng lại lịch.
 * Khởi động lại giữa khoảng khoá không thoát được: dịch vụ chạy lại và khoá tiếp.
 */
public class KhoiDongReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        CauHinh ch = new CauHinh(ctx);
        if (!ch.daDatMa()) return;

        NhatKy.ghi(ctx, "khoi-dong-lai", String.valueOf(intent.getAction()));

        // Đang trong khoảng khoá mà máy vừa bật lại thì khoá lại sau 1 phút.
        long bayGio = System.currentTimeMillis();
        if (ch.trongKhoangKhoa(bayGio)) {
            ch.datMocKhoa(bayGio + 60_000L);
        }
        LenLich.datLai(ctx);

        Intent i = new Intent(ctx, DichVuKhoa.class);
        i.setAction(DichVuKhoa.HANH_DONG_CANH_GIU);
        ctx.startForegroundService(i);
    }
}
