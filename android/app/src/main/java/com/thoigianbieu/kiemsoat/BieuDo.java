package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Biểu đồ 14 đêm gần nhất. Cột càng cao nghĩa là đi ngủ càng muộn.
 * Vẽ tay bằng Canvas — thêm một thư viện biểu đồ cho đúng 14 cái cột thì phí.
 */
public class BieuDo extends View {

    /** Thang đo giống hệt bản PWA: 21:00 tới 27:00 (tức 03:00 sáng). */
    private static final double DUOI = 21;
    private static final double TREN = 27;

    public static class Cot {
        public final String nhan;
        public final double giaTri;
        public final boolean tre;
        public final boolean coDuLieu;

        public Cot(String nhan, double giaTri, boolean tre, boolean coDuLieu) {
            this.nhan = nhan; this.giaTri = giaTri; this.tre = tre; this.coDuLieu = coDuLieu;
        }
    }

    private final List<Cot> cot = new ArrayList<>();
    private final Paint son = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chu = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BieuDo(Context ctx) { this(ctx, null); }

    public BieuDo(Context ctx, AttributeSet thuoc) {
        super(ctx, thuoc);
        chu.setColor(Color.parseColor("#64748B"));
        chu.setTextAlign(Paint.Align.CENTER);
        chu.setTextSize(dp(10));
    }

    public void datDuLieu(List<Cot> ds) {
        cot.clear();
        cot.addAll(ds);
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int rongSpec, int caoSpec) {
        int rong = resolveSize((int) dp(320), rongSpec);
        int cao = resolveSize((int) dp(160), caoSpec);
        setMeasuredDimension(rong, cao);
    }

    @Override
    protected void onDraw(Canvas v) {
        if (cot.isEmpty()) return;

        float caoNhan = dp(16);
        float caoVe = getHeight() - caoNhan - dp(4);
        float khoang = dp(3);
        float rongCot = (getWidth() - khoang * (cot.size() - 1)) / (float) cot.size();
        float banKinh = Math.min(dp(4), rongCot / 2);

        for (int i = 0; i < cot.size(); i++) {
            Cot c = cot.get(i);
            float trai = i * (rongCot + khoang);
            float phai = trai + rongCot;

            float tiLe;
            if (!c.coDuLieu) {
                tiLe = 0.10f;
                son.setColor(Color.parseColor("#1E293B"));
            } else {
                double g = Math.max(DUOI, Math.min(TREN, c.giaTri));
                tiLe = (float) ((g - DUOI) / (TREN - DUOI)) * 0.88f + 0.12f;
                son.setColor(Color.parseColor(c.tre ? "#F87171" : "#34D399"));
            }

            float caoCot = caoVe * tiLe;
            RectF o = new RectF(trai, caoVe - caoCot, phai, caoVe);
            v.drawRoundRect(o, banKinh, banKinh, son);

            v.drawText(c.nhan, (trai + phai) / 2f, getHeight() - dp(3), chu);
        }
    }
}
