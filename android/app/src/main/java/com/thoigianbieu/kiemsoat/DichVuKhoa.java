package com.thoigianbieu.kiemsoat;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
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
 * Dịch vụ nền giữ lịch và dựng mọi lớp phủ. Lo hai việc:
 *   - Khoá theo giờ ban đêm: tới giờ thì tắt màn hình, đòi mã hai lần.
 *   - Dùng ngắt quãng ban ngày: đếm thời gian màn hình bật, hết hạn mức thì
 *     bắt nghỉ.
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
    private static final int ID_THONG_BAO_NGAT_QUANG = 3;

    /** Chưa mở khoá được sau chừng này thì tắt màn hình lần nữa. */
    private static final long CHU_KY_KHOA_LAI = 120_000L;
    /** Nhịp soát hạn mức dùng ngắt quãng. */
    private static final long CHU_KY_SOAT_NQ = 20_000L;
    /** Hoãn khoá vì đang gọi điện lâu nhất chừng này rồi thôi. */
    private static final long HOAN_TOI_DA = 20 * 60_000L;

    private CauHinh ch;
    private NgatQuang nq;
    private WindowManager wm;
    private final Handler tay = new Handler(Looper.getMainLooper());

    private View lopKhoa;
    private View lopCanhBao;
    private View lopNghi;

    private int buoc = 1;        // 1 = chờ lần 1, 2 = đang đếm ngược, 3 = chờ lần 2
    private int conLai = 0;
    private Runnable nhipDem;
    private Runnable nhipDongHo;
    private Runnable canhGac;
    private Runnable nhipNgatQuang;
    private Runnable nhipNghi;
    private boolean daCanhBaoDot;
    private boolean khanCapChoXacNhan;
    /** Lúc bắt đầu hoãn khoá vì đang gọi điện. 0 = không hoãn. */
    private long hoanTuLuc;

    private BroadcastReceiver batTatManHinh;

    @Override
    public void onCreate() {
        super.onCreate();
        ch = new CauHinh(this);
        nq = new NgatQuang(ch);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        taoKenh();
        dangKyManHinh();

        // Dịch vụ vừa dựng lại giữa lúc màn hình đang bật thì phải đếm tiếp ngay.
        if (manHinhDangBat() && !mayDangKhoa()) {
            manHinhVaoDung(System.currentTimeMillis());
        }
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
        if (lopKhoa != null) return;
        if (!ch.daDatMa()) return;

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

        String phu;
        if (!ch.daDatMa()) {
            phu = getString(R.string.chua_dat_ma);
        } else if (ch.ngatQuangBat()) {
            phu = getString(R.string.khoa_luc, LenLich.gioPhut(ch.mocKhoa()))
                    + " · " + getString(R.string.ngat_quang_dang_bat, ch.nqPhutDung(), ch.nqPhutNghi());
        } else {
            phu = getString(R.string.khoa_luc, LenLich.gioPhut(ch.mocKhoa()));
        }

        Notification tb = new Notification.Builder(this, KENH_NEN)
                .setContentTitle(getString(R.string.ten_app))
                .setContentText(phu)
                .setStyle(new Notification.BigTextStyle().bigText(phu))
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

    private void baoNhanh(int idThongBao, String tieuDe, String noiDung) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.notify(idThongBao, new Notification.Builder(this, KENH_CANH_BAO)
                .setContentTitle(tieuDe)
                .setContentText(noiDung)
                .setStyle(new Notification.BigTextStyle().bigText(noiDung))
                .setSmallIcon(R.drawable.bieu_tuong)
                .setAutoCancel(true)
                .build());
    }

    /* ==================== CẢNH BÁO SẮP KHOÁ ==================== */

    private void hienCanhBao(int soPhut) {
        String noi = soPhut <= 1
                ? getString(R.string.canh_bao_mot_phut)
                : getString(R.string.canh_bao_nhieu_phut, soPhut, ch.khoangCachGiay());

        baoNhanh(ID_THONG_BAO_CANH_BAO, getString(R.string.sap_khoa, soPhut), noi);
        rung(300);
        NhatKy.ghi(this, "canh-bao", soPhut + " phút");

        hienDaiCanhBao(getString(R.string.sap_khoa, soPhut), noi);
    }

    /** Dải cảnh báo màu vàng bám trên đỉnh màn hình, tự biến mất sau 12 giây. */
    private void hienDaiCanhBao(String tieuDe, String noiDung) {
        if (!Settings.canDrawOverlays(this) || lopKhoa != null || lopNghi != null) return;

        goLopCanhBao();
        View v = LayoutInflater.from(this).inflate(R.layout.lop_phu_canh_bao, null);
        ((TextView) v.findViewById(R.id.tieu_de)).setText(tieuDe);
        ((TextView) v.findViewById(R.id.noi_dung)).setText(noiDung);

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

    /* ==================== KHOÁ THEO GIỜ ==================== */

    private void khoa() {
        if (lopKhoa != null) {
            khoaManHinhNeuDuoc();
            return;
        }
        if (!ch.daDatMa()) return;

        // Đang gọi điện thì hoãn lại, nhịp soát sau sẽ khoá bù.
        if (choPhepHoan()) {
            NhatKy.ghi(this, "hoan-khoa", "đang gọi điện");
            ch.datMocKhoa(System.currentTimeMillis() + 60_000L);
            LenLich.datLai(this);
            return;
        }
        hoanTuLuc = 0;

        goLopCanhBao();
        goLopNghi();
        NhatKy.ghi(this, "khoa", "mốc " + LenLich.gioPhut(ch.mocKhoa()));

        if (!Settings.canDrawOverlays(this)) {
            // Không có quyền lớp phủ thì vẫn tắt màn hình, chỉ là không đòi được mã.
            NhatKy.ghi(this, "thieu-quyen", "chưa cho phép hiển thị trên ứng dụng khác");
            khoaManHinhNeuDuoc();
            datMocSauKhiMo();
            return;
        }

        dungLopKhoa();
        khoaManHinhNeuDuoc();

        canhGac = new Runnable() {
            @Override
            public void run() {
                if (lopKhoa == null) return;
                NhatKy.ghi(DichVuKhoa.this, "khoa-lai", "vẫn chưa nhập đúng mã");
                khoaManHinhNeuDuoc();
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

        // KHÔNG dùng FLAG_LAYOUT_IN_SCREEN ở đây. Cờ đó ghim cửa sổ full màn
        // hình nên bàn phím không co lại được, và ô nhập mã bị che mất hoàn
        // toàn — đúng lỗi đã gặp trên máy thật. Bỏ cờ đi thì cửa sổ chừa chỗ
        // cho bàn phím, cộng với ScrollView bên trong là ô nhập luôn thấy được.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0,
                PixelFormat.OPAQUE);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

        try {
            wm.addView(khung, lp);
            lopKhoa = khung;
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-lop-khoa", String.valueOf(e.getMessage()));
            return;
        }

        // Bấm vào ô nào thì cuộn ô đó lên trên bàn phím.
        final android.widget.ScrollView cuon = khung.findViewById(R.id.cuon_khoa);
        View.OnFocusChangeListener khiNhanTieuDiem = (v, coTieuDiem) -> {
            if (!coTieuDiem || cuon == null) return;
            tay.postDelayed(() -> cuon.smoothScrollTo(0, Math.max(0, v.getTop() - dp(70))), 250);
        };
        o1.setOnFocusChangeListener(khiNhanTieuDiem);
        o2.setOnFocusChangeListener(khiNhanTieuDiem);

        // Đồng hồ chạy suốt lúc đang khoá, để biết mình đang thức muộn cỡ nào.
        final TextView dongHo = khung.findViewById(R.id.dong_ho_khoa);
        nhipDongHo = new Runnable() {
            @Override
            public void run() {
                if (lopKhoa == null) return;
                dongHo.setText(LuatGiacNgu.gioHienTai(System.currentTimeMillis()));
                tay.postDelayed(this, 1000L);
            }
        };
        tay.post(nhipDongHo);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
        if (nhipDongHo != null) tay.removeCallbacks(nhipDongHo);
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

        // Mở khoá đêm xong thì màn hình đang bật, nhưng sẽ không có tin bật màn
        // hình nào nữa để khởi động lại bộ đếm ngắt quãng. Không gọi tay ở đây
        // thì ngắt quãng nằm im suốt cả quãng vừa mở khoá được.
        if (manHinhDangBat()) manHinhVaoDung(System.currentTimeMillis());
    }

    /* ==================== DÙNG NGẮT QUÃNG ==================== */

    /**
     * Bật/tắt màn hình chỉ đăng ký được lúc chạy, không khai trong manifest
     * được. Đó cũng là lý do dịch vụ này phải sống thường trực.
     */
    private void dangKyManHinh() {
        batTatManHinh = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long bayGio = System.currentTimeMillis();
                String viec = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(viec)) {
                    manHinhRoiDung(bayGio);
                } else if (Intent.ACTION_USER_PRESENT.equals(viec)) {
                    manHinhVaoDung(bayGio);
                } else if (Intent.ACTION_SCREEN_ON.equals(viec)) {
                    // Máy không đặt khoá màn hình thì không có USER_PRESENT.
                    if (!mayDangKhoa()) manHinhVaoDung(bayGio);
                }
            }
        };
        IntentFilter loc = new IntentFilter();
        loc.addAction(Intent.ACTION_SCREEN_ON);
        loc.addAction(Intent.ACTION_SCREEN_OFF);
        loc.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(batTatManHinh, loc, Context.RECEIVER_NOT_EXPORTED);
    }

    private void manHinhVaoDung(long bayGio) {
        if (!nq.dangApDung(bayGio)) return;
        if (lopKhoa != null) return;   // đang khoá đêm, chuyện khác

        if (nq.dangNghi(bayGio)) {
            hienLopNghi();
            khoaManHinhNeuDuoc();
            return;
        }
        nq.batDauDung(bayGio);
        daCanhBaoDot = false;
        batNhipNgatQuang();
    }

    private void manHinhRoiDung(long bayGio) {
        nq.dungDem(bayGio);
        dungNhipNgatQuang();
        goLopNghi();   // màn hình tắt rồi thì lớp phủ để đó vô nghĩa
    }

    private void batNhipNgatQuang() {
        dungNhipNgatQuang();
        nhipNgatQuang = new Runnable() {
            @Override
            public void run() {
                soatNgatQuang();
                tay.postDelayed(this, CHU_KY_SOAT_NQ);
            }
        };
        tay.postDelayed(nhipNgatQuang, CHU_KY_SOAT_NQ);
    }

    private void dungNhipNgatQuang() {
        if (nhipNgatQuang != null) tay.removeCallbacks(nhipNgatQuang);
        nhipNgatQuang = null;
    }

    private void soatNgatQuang() {
        long bayGio = System.currentTimeMillis();
        if (lopKhoa != null) return;
        if (!nq.dangApDung(bayGio)) return;

        if (nq.dangNghi(bayGio)) {
            hienLopNghi();
            return;
        }

        long con = nq.conLai(bayGio);
        if (con <= 0) {
            vaoNghi(bayGio);
            return;
        }
        if (!daCanhBaoDot && con <= ch.nqCanhBaoPhut() * 60_000L) {
            daCanhBaoDot = true;
            int phut = (int) Math.max(1, Math.round(con / 60_000.0));
            String noi = getString(R.string.nq_sap_het_noi, phut, ch.nqPhutNghi());
            baoNhanh(ID_THONG_BAO_NGAT_QUANG, getString(R.string.nq_sap_het), noi);
            hienDaiCanhBao(getString(R.string.nq_sap_het), noi);
            rung(250);
        }
    }

    private void vaoNghi(long bayGio) {
        if (choPhepHoan()) {
            NhatKy.ghi(this, "hoan-nghi", "đang gọi điện");
            return;
        }
        hoanTuLuc = 0;
        nq.batDauNghi(bayGio);
        daCanhBaoDot = false;
        dungNhipNgatQuang();
        NhatKy.ghi(this, "ngat-quang", "hết hạn mức " + ch.nqPhutDung()
                + " phút, nghỉ " + ch.nqPhutNghi() + " phút");
        hienLopNghi();
        khoaManHinhNeuDuoc();
    }

    /** Lớp phủ nghỉ: chỉ có đồng hồ đếm ngược, không đòi mã. Hết giờ tự tan. */
    private void hienLopNghi() {
        if (lopNghi != null) return;
        if (!Settings.canDrawOverlays(this)) return;

        goLopCanhBao();
        khanCapChoXacNhan = false;

        KhungPhu khung = new KhungPhu(this);
        LayoutInflater.from(this).inflate(R.layout.lop_phu_nghi, khung, true);

        final TextView dem = khung.findViewById(R.id.dem_nghi);
        final TextView loiNhac = khung.findViewById(R.id.loi_nhac);
        final TextView thongKe = khung.findViewById(R.id.thong_ke_nghi);
        final Button nutKhanCap = khung.findViewById(R.id.nut_khan_cap);

        loiNhac.setText(R.string.nq_loi_nhac);
        thongKe.setText(getString(R.string.nq_thong_ke,
                CauHinh.doDai(ch.nqTongHomNay()), ch.nqSoDotHomNay()));

        if (nq.conLuotKhanCap()) {
            nutKhanCap.setVisibility(View.VISIBLE);
            nutKhanCap.setText(getString(R.string.thoat_khan_cap_con, nq.luotKhanCapConLai()));
            nutKhanCap.setOnClickListener(v -> {
                if (!khanCapChoXacNhan) {
                    // Một cú chạm là quá dễ. Bắt bấm lần thứ hai trong 5 giây.
                    khanCapChoXacNhan = true;
                    nutKhanCap.setText(R.string.xac_nhan_khan_cap);
                    tay.postDelayed(() -> {
                        khanCapChoXacNhan = false;
                        if (lopNghi != null) {
                            nutKhanCap.setText(getString(R.string.thoat_khan_cap_con,
                                    nq.luotKhanCapConLai()));
                        }
                    }, 5000L);
                    return;
                }
                ch.nqTangSoKhanCap();
                NhatKy.ghi(this, "khan-cap", "thoát quãng nghỉ sớm");
                nq.ketThucNghi(System.currentTimeMillis());
                goLopNghi();
                batNhipNgatQuang();
            });
        } else {
            nutKhanCap.setVisibility(View.GONE);
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);

        try {
            wm.addView(khung, lp);
            lopNghi = khung;
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-lop-nghi", String.valueOf(e.getMessage()));
            return;
        }

        nhipNghi = new Runnable() {
            @Override
            public void run() {
                long conMs = nq.conNghi(System.currentTimeMillis());
                if (conMs <= 0) {
                    NhatKy.ghi(DichVuKhoa.this, "het-nghi", "được dùng tiếp");
                    nq.ketThucNghi(System.currentTimeMillis());
                    goLopNghi();
                    if (manHinhDangBat()) batNhipNgatQuang();
                    return;
                }
                long giay = conMs / 1000;
                dem.setText(String.format(java.util.Locale.US, "%d:%02d", giay / 60, giay % 60));
                tay.postDelayed(this, 1000L);
            }
        };
        tay.post(nhipNghi);
    }

    private void goLopNghi() {
        if (nhipNghi != null) tay.removeCallbacks(nhipNghi);
        nhipNghi = null;
        if (lopNghi == null) return;
        try { wm.removeView(lopNghi); } catch (Exception ignore) { }
        lopNghi = null;
    }

    /* ==================== LẶT VẶT ==================== */

    private void khoaManHinhNeuDuoc() {
        if (choPhepHoan()) return;
        QuanTriReceiver.khoaNgay(this);
    }

    /**
     * Có được hoãn khoá vì đang gọi điện không.
     *
     * Phải có trần thời gian. MODE_IN_COMMUNICATION không chỉ có cuộc gọi thật:
     * khối ứng dụng ghi âm, trợ lý giọng nói, cả vài trò chơi cũng giữ chế độ
     * này. Hoãn vô hạn nghĩa là mở một ứng dụng như thế lên là thoát được khoá
     * cả đêm. Quá {@link #HOAN_TOI_DA} thì khoá, gọi hay không cũng khoá.
     */
    private boolean choPhepHoan() {
        if (!ch.nqKhongKhoaKhiGoi() || !dangGoiDien()) {
            hoanTuLuc = 0;
            return false;
        }
        long bayGio = System.currentTimeMillis();
        if (hoanTuLuc == 0) {
            hoanTuLuc = bayGio;
            return true;
        }
        if (bayGio - hoanTuLuc >= HOAN_TOI_DA) {
            NhatKy.ghi(this, "het-han-hoan", "hoãn quá lâu vì chế độ gọi, khoá luôn");
            hoanTuLuc = 0;
            return false;
        }
        return true;
    }

    private boolean dangGoiDien() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        int che = am.getMode();
        return che == AudioManager.MODE_IN_CALL
                || che == AudioManager.MODE_IN_COMMUNICATION
                || che == AudioManager.MODE_RINGTONE;
    }

    private boolean manHinhDangBat() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isInteractive();
    }

    private boolean mayDangKhoa() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        return km != null && km.isKeyguardLocked();
    }

    private void rung(int mili) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createOneShot(mili, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    public void onDestroy() {
        if (batTatManHinh != null) {
            try { unregisterReceiver(batTatManHinh); } catch (Exception ignore) { }
            batTatManHinh = null;
        }
        // Chốt nốt quãng đang dùng dở, đừng để mất thời gian đã đếm.
        if (ch != null && ch.nqBatDauPhien() > 0) {
            nq.dungDem(System.currentTimeMillis());
        }
        goLopCanhBao();
        goLopNghi();
        dungNhipNgatQuang();
        if (nhipDem != null) tay.removeCallbacks(nhipDem);
        if (nhipDongHo != null) tay.removeCallbacks(nhipDongHo);
        if (canhGac != null) tay.removeCallbacks(canhGac);
        if (lopKhoa != null) {
            try { wm.removeView(lopKhoa); } catch (Exception ignore) { }
            lopKhoa = null;
        }
        super.onDestroy();
    }
}
