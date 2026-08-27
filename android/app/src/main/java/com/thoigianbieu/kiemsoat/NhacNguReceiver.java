package com.thoigianbieu.kiemsoat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Lời nhắc giờ ngủ nổ. Nhắc xong đặt lại cho đêm mai. */
public class NhacNguReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String viec = intent.getStringExtra(NhacNgu.VIEC);
        try {
            NhacNgu.nhac(ctx, viec == null ? NhacNgu.VIEC_CHUAN_BI : viec);
        } catch (Exception e) {
            NhatKy.ghi(ctx, "loi-nhac", String.valueOf(e.getMessage()));
        }
        // Đặt lại cho đêm mai ngay tại đây: báo thức một lần đã dùng xong.
        NhacNgu.datLai(ctx);
    }
}
