package com.thoigianbieu.kiemsoat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Dịch vụ nền giữ lịch và dựng lớp phủ khoá.
 *
 * Vì sao dùng lớp phủ chứ không dùng Activity: từ Android 10, app chạy nền
 * không được tự mở Activity. Lớp phủ (SYSTEM_ALERT_WINDOW) thì dựng lúc nào
 * cũng được, lại nằm đè lên cả màn hình chính nên bấm Home cũng không thoát.
 */
public class DichVuKhoa extends Service {

    public static final String HANH_DONG_CANH_GIU = "canh_giu";
    public static final String HANH_DONG_CANH_BAO = "canh_bao";
    public static final String HANH_DONG_KHOA = "khoa";

    private static final String KENH_NEN = "chay_nen";
    private static final String KENH_CANH_BAO = "canh_bao";
    private static final int ID_THONG_BAO_NEN = 1;
    private static final int ID_THONG_BAO_CANH_BAO = 2;

    /** Chưa mở khoá được sau chừng này thì tắt màn hình lần nữa. */
    private static final long CHU_KY_KHOA_LAI = 120_000L;

    private CauHinh ch;
    private WindowManager wm;
    private final Handler tay = new Handler(Looper.getMainLooper());

    private View lopKhoa;
    private View lopCanhBao;

    private int buoc = 1;        // 1 = chờ lần 1, 2 = đang đếm ngược, 3 = chờ lần 2
    private int conLai = 0;
    private Runnable nhipDem;
    private Runnable canhGac;

    @Override
    public void onCreate() {
        super.onCreate();
        ch = new CauHinh(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        taoKenh();
    }

    @Override
    public int onStartCommand(Intent intent, int co, int id) {
        // Phải gọi ngay, nếu không hệ thống giết tiến trình sau 5 giây.
        chayNen();

        String hanhDong = intent != null ? intent.getAction() : HANH_DONG_CANH_GIU;
        if (HANH_DONG_CANH_BAO.equals(hanhDong)) {
            hienCanhBao(intent.getIntExtra(LenLich.SO_PHUT, 0));
        } else if (HANH_DONG_KHOA.equals(hanhDong)) {
            khoa();
        } else {
            soatLaiLich();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Nhịp nửa tiếng gọi vào đây. Nếu mốc khoá đã trôi qua mà không có gì xảy
     * ra — hệ thống dọn mất báo thức, hoặc máy tắt ngang qua mốc đó — thì khoá
     * ngay, còn không thì chỉ dựng lại lịch.
     */
    private void soatLaiLich() {
        if (!ch.daDatMa() || lopKhoa != null) return;

        long bayGio = System.currentTimeMillis();
        if (ch.mocKhoa() > 0 && bayGio >= ch.mocKhoa() && ch.trongKhoangKhoa(bayGio)) {
            NhatKy.ghi(this, "soat-lai", "mốc khoá đã trôi qua, khoá bù");
            khoa();
            return;
        }
        LenLich.datLai(this);
    }

    /* ==================== THÔNG BÁO ==================== */

    private void taoKenh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel nen = new NotificationChannel(
                KENH_NEN, getString(R.string.kenh_nen), NotificationManager.IMPORTANCE_LOW);
        nen.setShowBadge(false);
        nm.createNotificationChannel(nen);

        NotificationChannel canhBao = new NotificationChannel(
                KENH_CANH_BAO, getString(R.string.kenh_canh_bao), NotificationManager.IMPORTANCE_HIGH);
        canhBao.enableVibration(true);
        nm.createNotificationChannel(canhBao);
    }

    private void chayNen() {
        PendingIntent moApp = PendingIntent.getActivity(this, 0,
                new Intent(this, CaiDatActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String phu = ch.daDatMa() && ch.mocKhoa() > 0
                ? getString(R.string.khoa_luc, LenLich.gioPhut(ch.mocKhoa()))
                : getString(R.string.chua_dat_ma);

        Notification tb = new Notification.Builder(this, KENH_NEN)
                .setContentTitle(getString(R.string.ten_app))
                .setContentText(phu)
                .setSmallIcon(R.drawable.bieu_tuong)
                .setContentIntent(moApp)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID_THONG_BAO_NEN, tb, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_THONG_BAO_NEN, tb);
        }
    }

    /* ==================== CẢNH BÁO ==================== */

    private void hienCanhBao(int soPhut) {
        String noi = soPhut <= 1
                ? getString(R.string.canh_bao_mot_phut)
                : getString(R.string.canh_bao_nhieu_phut, soPhut, ch.khoangCachGiay());

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(ID_THONG_BAO_CANH_BAO, new Notification.Builder(this, KENH_CANH_BAO)
                    .setContentTitle(getString(R.string.sap_khoa, soPhut))
                    .setContentText(noi)
                    .setStyle(new Notification.BigTextStyle().bigText(noi))
                    .setSmallIcon(R.drawable.bieu_tuong)
                    .setAutoCancel(true)
                    .build());
        }
        rung(300);
        NhatKy.ghi(this, "canh-bao", soPhut + " phút");

        if (!Settings.canDrawOverlays(this) || lopKhoa != null) return;

        goLopCanhBao();
        View v = LayoutInflater.from(this).inflate(R.layout.lop_phu_canh_bao, null);
        ((TextView) v.findViewById(R.id.tieu_de)).setText(getString(R.string.sap_khoa, soPhut));
        ((TextView) v.findViewById(R.id.noi_dung)).setText(noi);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;

        try {
            wm.addView(v, lp);
            lopCanhBao = v;
            tay.postDelayed(this::goLopCanhBao, 12_000L);
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-canh-bao", String.valueOf(e.getMessage()));
        }
    }

    private void goLopCanhBao() {
        if (lopCanhBao == null) return;
        try { wm.removeView(lopCanhBao); } catch (Exception ignore) { }
        lopCanhBao = null;
    }

    /* ==================== KHOÁ ==================== */

    private void khoa() {
        if (lopKhoa != null) {
            QuanTriReceiver.khoaNgay(this);
            return;
        }
        if (!ch.daDatMa()) return;

        goLopCanhBao();
        NhatKy.ghi(this, "khoa", "mốc " + LenLich.gioPhut(ch.mocKhoa()));

        if (!Settings.canDrawOverlays(this)) {
            // Không có quyền lớp phủ thì vẫn tắt màn hình, chỉ là không đòi được mã.
            NhatKy.ghi(this, "thieu-quyen", "chưa cho phép hiển thị trên ứng dụng khác");
            QuanTriReceiver.khoaNgay(this);
            datMocSauKhiMo();
            return;
        }

        dungLopKhoa();
        QuanTriReceiver.khoaNgay(this);

        canhGac = new Runnable() {
            @Override
            public void run() {
                if (lopKhoa == null) return;
                NhatKy.ghi(DichVuKhoa.this, "khoa-lai", "vẫn chưa nhập đúng mã");
                QuanTriReceiver.khoaNgay(DichVuKhoa.this);
                tay.postDelayed(this, CHU_KY_KHOA_LAI);
            }
        };
        tay.postDelayed(canhGac, CHU_KY_KHOA_LAI);
    }

    private void dungLopKhoa() {
        KhungPhu khung = new KhungPhu(this);
        LayoutInflater.from(this).inflate(R.layout.lop_phu_khoa, khung, true);

        final EditText o1 = khung.findViewById(R.id.o_ma_1);
        final EditText o2 = khung.findViewById(R.id.o_ma_2);
        final Button nut1 = khung.findViewById(R.id.nut_1);
        final Button nut2 = khung.findViewById(R.id.nut_2);
        final TextView dem = khung.findViewById(R.id.dem_nguoc);
        final TextView trangThai = khung.findViewById(R.id.trang_thai);
        final TextView huongDan = khung.findViewById(R.id.huong_dan);

        huongDan.setText(getString(R.string.huong_dan_mo_khoa, ch.khoangCachGiay()));

        buoc = 1;
        conLai = 0;
        o2.setEnabled(false);
        nut2.setEnabled(false);

        nut1.setOnClickListener(v -> {
            if (buoc != 1) return;
            if (ch.maDung(o1.getText().toString())) {
                buoc = 2;
                conLai = ch.khoangCachGiay();
                o1.setEnabled(false);
                nut1.setEnabled(false);
                trangThai.setText("");
                dem.setText(getString(R.string.cho_them_giay, conLai));
                chayDemNguoc(dem, o2, nut2);
            } else {
                trangThai.setText(R.string.sai_ma);
                o1.setText("");
                rung(400);
                NhatKy.ghi(this, "sai-ma", "lần 1");
            }
        });

        nut2.setOnClickListener(v -> {
            if (buoc != 3) return;
            if (ch.maDung(o2.getText().toString())) {
                moKhoa();
            } else {
                trangThai.setText(R.string.sai_ma_lan_2);
                rung(400);
                NhatKy.ghi(this, "sai-ma", "lần 2 - phải làm lại từ đầu");
                buoc = 1;
                conLai = 0;
                if (nhipDem != null) tay.removeCallbacks(nhipDem);
                o1.setText("");
                o2.setText("");
                o1.setEnabled(true);
                nut1.setEnabled(true);
                o2.setEnabled(false);
                nut2.setEnabled(false);
                dem.setText(R.string.cho_sau_lan_1);
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

        try {
            wm.addView(khung, lp);
            lopKhoa = khung;
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-lop-khoa", String.valueOf(e.getMessage()));
        }
    }

    private void chayDemNguoc(final TextView dem, final EditText o2, final Button nut2) {
        if (nhipDem != null) tay.removeCallbacks(nhipDem);
        nhipDem = new Runnable() {
            @Override
            public void run() {
                if (buoc != 2 || lopKhoa == null) return;
                conLai--;
                if (conLai > 0) {
                    dem.setText(getString(R.string.cho_them_giay, conLai));
                    tay.postDelayed(this, 1000L);
                } else {
                    buoc = 3;
                    dem.setText(R.string.duoc_roi);
                    o2.setEnabled(true);
                    nut2.setEnabled(true);
                    o2.requestFocus();
                }
            }
        };
        tay.postDelayed(nhipDem, 1000L);
    }

    private void moKhoa() {
        NhatKy.ghi(this, "mo-khoa", "nhập đúng mã hai lần");
        if (nhipDem != null) tay.removeCallbacks(nhipDem);
        if (canhGac != null) tay.removeCallbacks(canhGac);
        if (lopKhoa != null) {
            try { wm.removeView(lopKhoa); } catch (Exception ignore) { }
            lopKhoa = null;
        }
        datMocSauKhiMo();
    }

    private void datMocSauKhiMo() {
        ch.datMocKhoa(ch.mocSauKhiMo(System.currentTimeMillis()));
        LenLich.datLai(this);
        chayNen();
    }

    /* ==================== LẶT VẶT ==================== */

    private void rung(int mili) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createOneShot(mili, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    public void onDestroy() {
        goLopCanhBao();
        if (nhipDem != null) tay.removeCallbacks(nhipDem);
        if (canhGac != null) tay.removeCallbacks(canhGac);
        if (lopKhoa != null) {
            try { wm.removeView(lopKhoa); } catch (Exception ignore) { }
            lopKhoa = null;
        }
        super.onDestroy();
    }
}
