package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * Khung ngoài của lớp phủ khoá. Nuốt phím Quay lại để không thoát được bằng
 * cách bấm Back. Phím Home thì Android không cho chặn — nhưng bấm Home cũng
 * vô ích, vì lớp phủ nằm đè lên cả màn hình chính.
 */
public class KhungPhu extends FrameLayout {

    public KhungPhu(Context ctx) {
        super(ctx);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent su) {
        if (su.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (su.getAction() == KeyEvent.ACTION_UP) {
                NhatKy.ghi(getContext(), "ne-tranh", "bấm phím Quay lại khi đang khoá");
            }
            return true;
        }
        return super.dispatchKeyEvent(su);
    }
}
