package com.thoigianbieu.kiemsoat;

import android.app.AlarmManager;
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
    public static final String HANH_DONG_DEM_LUI = "dem_lui";
    public static final String HANH_DONG_THONG_BAO_BI_VUOT = "thong_bao_bi_vuot";
    public static final String MOC_TAT = "moc_tat";

    /** Để màn hình Khoá máy biết dịch vụ còn sống hay không. */
    public static volatile boolean dangChay;

    /**
     * Kênh mới, mức MIN. Không sửa được mức của kênh cũ — Android khoá cứng cấu
     * hình kênh sau lần tạo đầu tiên, muốn đổi phải tạo kênh khác và xoá kênh cũ.
     */
    private static final String KENH_NEN = "chay_nen_min";
    private static final String KENH_NEN_CU = "chay_nen";
    private static final String KENH_CANH_BAO = "canh_bao";
    private static final int ID_THONG_BAO_NEN = 1;
    private static final int ID_THONG_BAO_CANH_BAO = 2;
    private static final int ID_THONG_BAO_NGAT_QUANG = 3;

    /** Chưa mở khoá được sau chừng này thì tắt màn hình lần nữa. */
    private static final long CHU_KY_KHOA_LAI = 120_000L;
    /**
     * Nhịp soát hạn mức dùng ngắt quãng. Soát dày khi sắp hết đợt, thưa khi còn
     * xa — cùng một kết quả nhưng ít đánh thức CPU hơn hẳn. Màn hình tắt là
     * ngừng hẳn, không có nhịp nào chạy.
     */
    private static final long SOAT_NQ_DAY = 10_000L;
    private static final long SOAT_NQ_THUA = 90_000L;
    /** Soát lại lịch khi bật màn hình, nhưng đừng làm quá dày. */
    private static final long GIAN_CACH_SOAT_LICH = 5L * 60 * 1000;
    /** Hoãn khoá vì đang gọi điện lâu nhất chừng này rồi thôi. */
    private static final long HOAN_TOI_DA = 20 * 60_000L;

    private CauHinh ch;
    private NgatQuang nq;
    private WindowManager wm;
    private final Handler tay = new Handler(Looper.getMainLooper());

    private View lopKhoa;
    private View lopCanhBao;
    private View lopNghi;
    private View lopDemLui;

    private int buoc = 1;        // 1 = chờ lần 1, 2 = đang đếm ngược, 3 = chờ lần 2
    private int conLai = 0;
    private Runnable nhipDem;
    private Runnable nhipDongHo;
    private Runnable canhGac;
    private Runnable nhipNgatQuang;
    private Runnable nhipNghi;
    private Runnable nhipDemLui;
    private boolean daCanhBaoDot;
    private boolean khanCapChoXacNhan;
    /** Lúc bắt đầu hoãn khoá vì đang gọi điện. 0 = không hoãn. */
    private long hoanTuLuc;
    private long lanSoatLichCuoi;

    private BroadcastReceiver batTatManHinh;

    @Override
    public void onCreate() {
        super.onCreate();
        dangChay = true;
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
        } else if (HANH_DONG_DEM_LUI.equals(hanhDong)) {
            hienDemLui(ch.mocKhoa(), getString(R.string.sap_tat_man_hinh));
        } else if (HANH_DONG_THONG_BAO_BI_VUOT.equals(hanhDong)) {
            // chayNen() ở trên đã dựng lại thông báo rồi, chỉ cần ghi lại.
            NhatKy.ghi(this, "thong-bao-bi-vuot", "đã dựng lại thông báo chạy nền");
        } else {
            soatLaiToanBo();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Vuốt app khỏi danh sách ứng dụng gần đây.
     *
     * Mặc định Android gọi hàm này rồi có thể giết luôn tiến trình — nghĩa là
     * chỉ một cú vuốt là tắt được phần gác giờ. Không chấp nhận được với một
     * app mà cả tác dụng nằm ở chỗ nó luôn chạy. Đặt hẹn giờ dựng lại dịch vụ
     * sau 2 giây; báo thức là một trong số ít đường được phép dựng dịch vụ
     * tiền cảnh từ nền.
     */
    @Override
    public void onTaskRemoved(Intent y) {
        NhatKy.ghi(this, "vuot-khoi-gan-day", "hẹn dựng lại dịch vụ sau 2 giây");
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent i = new Intent(this, DichVuKhoa.class);
            i.setAction(HANH_DONG_CANH_GIU);
            PendingIntent pi = PendingIntent.getForegroundService(this, 9100, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 2000, pi);
            }
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-dung-lai", String.valueOf(e.getMessage()));
        }
        super.onTaskRemoved(y);
    }

    /**
     * Soát lại cả lịch khoá lẫn bộ đếm ngắt quãng.
     *
     * Bộ đếm ngắt quãng vốn chỉ khởi động khi có tin BẬT MÀN HÌNH. Bật tính
     * năng trong Cài đặt rồi dùng máy tiếp mà không tắt màn hình lần nào thì
     * không có tin đó, và bộ đếm nằm im vĩnh viễn — đúng lỗi user gặp: bật
     * ngắt quãng xong xem YouTube 15 phút không thấy cảnh báo nào.
     */
    private void soatLaiToanBo() {
        soatLaiLich();

        long bayGio = System.currentTimeMillis();
        if (nq.dangApDung(bayGio) && manHinhDangBat() && !mayDangKhoa() && lopKhoa == null) {
            manHinhVaoDung(bayGio);
        }
        ghiTrangThaiNgatQuang();
    }

    /** Một dòng nhật ký đủ để chẩn đoán từ xa, khỏi phải đoán mò. */
    private void ghiTrangThaiNgatQuang() {
        if (!ch.ngatQuangBat()) {
            NhatKy.ghi(this, "nq-tat", "tính năng dùng ngắt quãng đang tắt");
            return;
        }
        long bayGio = System.currentTimeMillis();
        NhatKy.ghi(this, "nq-trang-thai",
                "đã dùng " + (nq.daDung(bayGio) / 60_000) + "/" + ch.nqPhutDung() + " phút"
                        + (nq.dangNghi(bayGio) ? ", đang nghỉ" : "")
                        + (nq.dangApDung(bayGio) ? "" : ", NGOÀI khung giờ")
                        + (manHinhDangBat() ? ", màn hình bật" : ", màn hình tắt")
                        + (nhipNgatQuang != null ? ", nhịp đang chạy" : ", NHỊP CHƯA CHẠY"));
    }

    /**
     * Mốc khoá đã trôi qua mà không có gì xảy ra — hệ thống dọn mất báo thức,
     * hoặc máy tắt ngang qua mốc đó — thì khoá bù, còn không thì dựng lại lịch.
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

        // MIN: không có icon trên thanh trạng thái, nằm gọn ở mục im lặng dưới
        // cùng. Android bắt buộc dịch vụ tiền cảnh phải có thông báo, không thể
        // giấu hẳn — đây là mức kín đáo nhất còn hợp lệ.
        NotificationChannel nen = new NotificationChannel(
                KENH_NEN, getString(R.string.kenh_nen), NotificationManager.IMPORTANCE_MIN);
        nen.setShowBadge(false);
        nen.setSound(null, null);
        nen.enableVibration(false);
        nen.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(nen);
        try { nm.deleteNotificationChannel(KENH_NEN_CU); } catch (Exception ignore) { }

        NotificationChannel canhBao = new NotificationChannel(
                KENH_CANH_BAO, getString(R.string.kenh_canh_bao), NotificationManager.IMPORTANCE_HIGH);
        canhBao.enableVibration(true);
        nm.createNotificationChannel(canhBao);
    }

    private void chayNen() {
        Intent yMoApp = new Intent(this, ChinhActivity.class);
        yMoApp.putExtra(ChinhActivity.MO_TAB, ChinhActivity.TAB_MAY);
        PendingIntent moApp = PendingIntent.getActivity(this, 0, yMoApp,
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

        Notification.Builder b = new Notification.Builder(this, KENH_NEN)
                .setContentTitle(getString(R.string.ten_app))
                .setContentText(phu)
                .setStyle(new Notification.BigTextStyle().bigText(phu))
                .setSmallIcon(R.drawable.bieu_tuong)
                .setContentIntent(moApp)
                .setOngoing(true)
                // Kênh đã đặt mức MIN, tắt âm và tắt rung; setPriority chỉ để
                // các máy cũ hiểu. (setSilent là của AndroidX, không có ở đây.)
                .setPriority(Notification.PRIORITY_MIN)
                // Vuốt bỏ thì cứ để nó biến mất, không dựng lại — user không
                // muốn thấy nó. Chỉ ghi một dòng nhật ký cho biết đã bị vuốt;
                // dịch vụ vẫn chạy y nguyên.
                .setDeleteIntent(PendingIntent.getForegroundService(this, 9200,
                        new Intent(this, DichVuKhoa.class)
                                .setAction(HANH_DONG_THONG_BAO_BI_VUOT),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Hoãn hiện 10 giây: mấy lần dựng dịch vụ chớp nhoáng sẽ không kịp
            // loé lên thông báo nào.
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED);
        }
        Notification tb = b.build();

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
        goLopDemLui();
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

        // Giữ FLAG_LAYOUT_IN_SCREEN để lớp phủ che kín cả thanh trạng thái —
        // bỏ cờ này đi là kéo được thanh thông báo xuống, thủng tác dụng khoá.
        // Đổi lại cửa sổ không tự co cho bàn phím, nên bên dưới tự đo bàn phím
        // rồi tự chừa chỗ và đẩy ô nhập lên.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

        try {
            wm.addView(khung, lp);
            lopKhoa = khung;
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-lop-khoa", String.valueOf(e.getMessage()));
            return;
        }

        final android.widget.ScrollView cuon = khung.findViewById(R.id.cuon_khoa);
        final int demDuoiGoc = cuon.getPaddingBottom();

        GiaoDien.theoDoiBanPhim(khung, caoBanPhim -> {
            cuon.setPadding(cuon.getPaddingLeft(), cuon.getPaddingTop(),
                    cuon.getPaddingRight(), demDuoiGoc + caoBanPhim);
            if (caoBanPhim > 0) cuonToiODangGo(cuon, o1, o2);
        });

        // Bấm vào ô nào thì cuộn ô đó lên trên bàn phím.
        View.OnFocusChangeListener khiNhanTieuDiem = (v, coTieuDiem) -> {
            if (!coTieuDiem) return;
            tay.postDelayed(() -> cuonToiODangGo(cuon, o1, o2), 250);
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

    /** Cuộn sao cho ô đang gõ nằm gần đỉnh, chắc chắn nằm trên bàn phím. */
    private void cuonToiODangGo(android.widget.ScrollView cuon, View o1, View o2) {
        View dangGo = o2.hasFocus() ? o2 : o1;
        int y = 0;
        View v = dangGo;
        while (v != null && v != cuon) {
            y += v.getTop();
            if (!(v.getParent() instanceof View)) break;
            v = (View) v.getParent();
        }
        final int dich = Math.max(0, y - dp(24));
        cuon.post(() -> cuon.smoothScrollTo(0, dich));
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
                    soatLichKhiThucDay(bayGio);
                    manHinhVaoDung(bayGio);
                } else if (Intent.ACTION_SCREEN_ON.equals(viec)) {
                    soatLichKhiThucDay(bayGio);
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

    /**
     * Bật màn hình là dịp soát lại lịch gần như miễn phí: máy đang thức sẵn.
     * Nhờ vậy nhịp báo thức nền mới thưa xuống được còn hai tiếng một lần mà
     * vẫn tự hồi phục nhanh.
     */
    private void soatLichKhiThucDay(long bayGio) {
        if (bayGio - lanSoatLichCuoi < GIAN_CACH_SOAT_LICH) return;
        lanSoatLichCuoi = bayGio;
        soatLaiLich();
    }

    private void manHinhVaoDung(long bayGio) {
        if (!nq.dangApDung(bayGio)) return;
        if (lopKhoa != null) return;   // đang khoá đêm, chuyện khác

        if (nq.dangNghi(bayGio)) {
            NhatKy.ghi(this, "nq-chan-mo-khoa",
                    "đang nghỉ, còn " + (nq.conNghi(bayGio) / 1000) + " giây");
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
        // Tính năng đang tắt hoặc ngoài khung giờ thì không đếm nhịp nào cả.
        long bayGio = System.currentTimeMillis();
        if (!nq.dangApDung(bayGio)) return;
        NhatKy.ghi(this, "nq-bat-nhip",
                "đã dùng " + (nq.daDung(bayGio) / 60_000) + "/" + ch.nqPhutDung() + " phút");

        nhipNgatQuang = new Runnable() {
            @Override
            public void run() {
                long cho = soatNgatQuang();
                if (cho > 0) tay.postDelayed(this, cho);
                else nhipNgatQuang = null;
            }
        };
        tay.postDelayed(nhipNgatQuang, SOAT_NQ_DAY);
    }

    private void dungNhipNgatQuang() {
        if (nhipNgatQuang != null) tay.removeCallbacks(nhipNgatQuang);
        nhipNgatQuang = null;
    }

    /** Trả về số mili giây tới lần soát kế tiếp. 0 nghĩa là thôi soát. */
    private long soatNgatQuang() {
        long bayGio = System.currentTimeMillis();
        if (lopKhoa != null) return SOAT_NQ_THUA;
        if (!nq.dangApDung(bayGio)) return 0;

        if (nq.dangNghi(bayGio)) {
            hienLopNghi();
            return 0;   // đồng hồ đếm ngược của lớp nghỉ lo tiếp
        }

        long con = nq.conLai(bayGio);
        if (con <= 0) {
            vaoNghi(bayGio);
            return 0;
        }
        if (!daCanhBaoDot && con <= ch.nqCanhBaoPhut() * 60_000L) {
            daCanhBaoDot = true;
            int phut = (int) Math.max(1, Math.round(con / 60_000.0));
            String noi = getString(R.string.nq_sap_het_noi, phut, ch.nqPhutNghi());
            baoNhanh(ID_THONG_BAO_NGAT_QUANG, getString(R.string.nq_sap_het), noi);
            hienDaiCanhBao(getString(R.string.nq_sap_het), noi);
            rung(250);
        }

        // Mười giây cuối: hiện đồng hồ đếm ngược nổi và soát từng giây.
        if (con <= 10_500) {
            hienDemLui(bayGio + con, getString(R.string.sap_het_dot_dung));
            return 500;
        }

        // Còn xa thì soát thưa, sắp hết thì soát dày.
        long cho = Math.max(SOAT_NQ_DAY, Math.min(SOAT_NQ_THUA, con / 3));
        // Nhưng đừng nhảy qua mốc 10 giây cuối, nếu không đồng hồ đếm ngược
        // hiện ra khi chỉ còn 4 giây — đúng lỗi vừa thấy trên máy thật.
        if (con - cho < 10_000) cho = Math.max(500, con - 10_000);
        return cho;
    }

    private void vaoNghi(long bayGio) {
        if (choPhepHoan()) {
            NhatKy.ghi(this, "hoan-nghi", "đang gọi điện");
            return;
        }
        hoanTuLuc = 0;
        goLopDemLui();
        nq.batDauNghi(bayGio);
        daCanhBaoDot = false;
        NhatKy.ghi(this, "ngat-quang", "hết hạn mức " + ch.nqPhutDung()
                + " phút, nghỉ " + ch.nqPhutNghi() + " phút");
        hienLopNghi();
        khoaManHinhNeuDuoc();
    }

    /** Lớp phủ nghỉ: chỉ có đồng hồ đếm ngược, không đòi mã. Hết giờ tự tan. */
    private void hienLopNghi() {
        if (lopNghi != null) {
            NhatKy.ghi(this, "nq-lop-nghi", "đã hiện sẵn rồi, bỏ qua");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            NhatKy.ghi(this, "nq-lop-nghi", "KHÔNG dựng được: thiếu quyền lớp phủ");
            return;
        }

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
            NhatKy.ghi(this, "nq-lop-nghi", "đã dựng, còn "
                    + (nq.conNghi(System.currentTimeMillis()) / 1000) + " giây");
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

    /* ==================== ĐỒNG HỒ ĐẾM NGƯỢC ==================== */

    /**
     * Đồng hồ đếm ngược nổi ở khoảng 1/5 phía trên màn hình, cho những giây
     * cuối trước khi tắt màn hình.
     *
     * Không nhận chạm (FLAG_NOT_TOUCHABLE) nên không cản việc đang làm dở —
     * chỉ để biết còn mấy giây mà kịp lưu lại.
     */
    private void hienDemLui(final long mocTat, String lyDo) {
        if (!Settings.canDrawOverlays(this)) return;
        // Đã có đồng hồ rồi thì để yên cho nó đếm nốt. Dựng lại mỗi nửa giây
        // vừa nhấp nháy vừa đầy nhật ký.
        if (lopDemLui != null) return;
        long con = mocTat - System.currentTimeMillis();
        if (con <= 0 || con > 60_000) return;

        View v = LayoutInflater.from(this).inflate(R.layout.lop_phu_dem_lui, null);
        final TextView so = v.findViewById(R.id.dem_lui_so);
        ((TextView) v.findViewById(R.id.dem_lui_ly_do)).setText(lyDo);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = getResources().getDisplayMetrics().heightPixels / 5;

        try {
            wm.addView(v, lp);
            lopDemLui = v;
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-dem-lui", String.valueOf(e.getMessage()));
            return;
        }
        NhatKy.ghi(this, "dem-lui", lyDo + ", còn " + (con / 1000) + " giây");
        rung(150);

        nhipDemLui = new Runnable() {
            @Override
            public void run() {
                long conLai = mocTat - System.currentTimeMillis();
                if (conLai <= 0 || lopDemLui == null) {
                    goLopDemLui();
                    return;
                }
                so.setText(String.valueOf((int) Math.ceil(conLai / 1000.0)));
                tay.postDelayed(this, 200);
            }
        };
        tay.post(nhipDemLui);
    }

    private void goLopDemLui() {
        if (nhipDemLui != null) tay.removeCallbacks(nhipDemLui);
        nhipDemLui = null;
        if (lopDemLui == null) return;
        try { wm.removeView(lopDemLui); } catch (Exception ignore) { }
        lopDemLui = null;
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
        dangChay = false;
        goLopDemLui();
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
