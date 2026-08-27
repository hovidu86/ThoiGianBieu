package com.thoigianbieu.kiemsoat;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Quyền quản trị thiết bị. Chỉ dùng đúng một việc: gọi lockNow() để tắt màn hình.
 */
public class QuanTriReceiver extends DeviceAdminReceiver {

    public static ComponentName thanhPhan(Context ctx) {
        return new ComponentName(ctx.getApplicationContext(), QuanTriReceiver.class);
    }

    public static boolean daBat(Context ctx) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(thanhPhan(ctx));
    }

    /** Tắt màn hình ngay. Trả về false nếu chưa được cấp quyền. */
    public static boolean khoaNgay(Context ctx) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isAdminActive(thanhPhan(ctx))) return false;
        try {
            dpm.lockNow();
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        return ctx.getString(R.string.canh_bao_go_quyen);
    }

    @Override
    public void onDisabled(Context ctx, Intent intent) {
        NhatKy.ghi(ctx, "go-quyen-quan-tri", "người dùng tắt quyền khoá màn hình");
    }
}
