package com.thoigianbieu.kiemsoat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình chính: module Giấc ngủ với ba tab Ghi nhận / Thống kê / Cài đặt.
 * Nút thứ tư mở thẳng màn hình Kiểm soát máy.
 *
 * Chuyển nguyên từ bản PWA sang. Cùng luật, cùng Google Sheet, cùng mô hình
 * dữ liệu — chỉ khác là chạy gốc nên nhắc nhở và đồng bộ không phụ thuộc vào
 * việc trình duyệt có đang mở hay không.
 */
public class ChinhActivity extends Activity {

    public static final String MO_TAB = "mo_tab";
    public static final String GHI_NGAY = "ghi_ngay";
    /** Lối tắt khi giữ icon app trên màn hình chính. */
    public static final String VIEC_GHI_NGAY = "com.thoigianbieu.kiemsoat.GHI_NGAY";
    public static final String VIEC_CHUAN_BI = "com.thoigianbieu.kiemsoat.CHUAN_BI";
    public static final int TAB_GHI_NHAN = 0;
    public static final int TAB_THONG_KE = 1;
    public static final int TAB_CAI_DAT = 2;
    public static final int TAB_MAY = 3;

    private KhoGiacNgu kho;
    private ManKiemSoat manMay;
    private final Handler tay = new Handler(Looper.getMainLooper());
    private Runnable nhip;
    private boolean dangGui;

    private ViewFlipper trang;
    private LinearLayout[] nutTab;
    private TextView[] hinhTab, chuTab;
    private TextView phuDe, chipDongBo;

    // Tab Ghi nhận
    private TextView uiSoDu, uiChuoi, demNhan, demChinh, demPhu, tieuDeBuoc, goiYBuoc,
            xemTruocLoi, xemTruocThem, xemTruocTien;
    private LinearLayout dsBuoc, dsGanDay;
    // Ba ô này là TextView chứ không phải EditText: bấm vào mở bảng chọn của hệ
    // thống. Gõ tay "1030" mà app không hiểu là lỗi đã gặp trên máy thật.
    private TextView oDem, oGioNgu, oGioDay;

    // Tab Thống kê
    private BieuDo bieuDo;
    private TextView tkThuong, tkPhat;
    private LinearLayout dsChiSo, dsLichSu;

    // Tab Cài đặt
    private EditText oUrl, oPhatVuot, oThuongThoiQuen;
    private TextView oTruocGio, oMatChuoi, oNhacLuc, tinKetNoi, tinCapNhat;
    private Button nutCapNhat;
    private LinearLayout dsMoc, dsMocChuoi, dsBuocSua;
    private CapNhat.BanMoi banMoiDangCho;

    private final List<HangMoc> hangMoc = new ArrayList<>();
    private final List<HangMocChuoi> hangMocChuoi = new ArrayList<>();
    private final List<HangBuoc> hangBuoc = new ArrayList<>();

    /* ==================== VÒNG ĐỜI ==================== */

    @Override
    protected void onCreate(Bundle luuTruoc) {
        super.onCreate(luuTruoc);
        setContentView(R.layout.man_chinh);
        kho = KhoGiacNgu.cua(this);

        timView();
        GiaoDien.chuaChoThanhHeThong(findViewById(R.id.goc),
                findViewById(R.id.dau_trang), findViewById(R.id.thanh_duoi));
        // Tab Khoá máy dùng chung khung này nên thanh tab luôn còn đó.
        manMay = new ManKiemSoat(this, findViewById(R.id.cuon_may));
        gan();
        napCaiDatVaoO();
        veTatCa();

        xinQuyenThongBao();
        NhacNgu.datLai(this);
        khoiDongDichVuKhoa();

        tinCapNhat.setText(getString(R.string.phien_ban_dang_dung,
                CapNhat.tenPhienBanHienTai(this)));

        xuLyYDinh(getIntent());
        tay.postDelayed(new Runnable() {
            @Override
            public void run() {
                dayNhungCaiChuaGui(true);
                // Tự dòm bản mới, nhiều nhất 6 tiếng một lần.
                if (CapNhat.toiLucTuKiemTra(ChinhActivity.this)) kiemTraBanMoi(true);
            }
        }, 1200);
    }

    @Override
    protected void onNewIntent(Intent y) {
        super.onNewIntent(y);
        setIntent(y);
        xuLyYDinh(y);
    }

    private void xuLyYDinh(Intent y) {
        if (y == null) return;
        int tab = y.getIntExtra(MO_TAB, -1);
        if (tab >= 0) chuyenTab(tab);

        String viec = y.getAction();
        if (y.getBooleanExtra(GHI_NGAY, false) || VIEC_GHI_NGAY.equals(viec)) {
            chuyenTab(TAB_GHI_NHAN);
            dienGioBayGio();
        } else if (VIEC_CHUAN_BI.equals(viec)) {
            chuyenTab(TAB_GHI_NHAN);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (kho.doiDemNeuCan()) sangDemMoi();
        veDemNguoc();
        if (manMay != null) manMay.capNhat();
        nhip = new Runnable() {
            @Override
            public void run() {
                if (kho.doiDemNeuCan()) sangDemMoi();
                veDemNguoc();
                tay.postDelayed(this, 30_000L);
            }
        };
        tay.postDelayed(nhip, 30_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nhip != null) tay.removeCallbacks(nhip);
    }

    /**
     * Qua mốc 18:00 là sang "đêm" mới. Phải điền lại ô Đêm, nếu không nó còn
     * giữ ngày hôm qua và một cú bấm Xác nhận sẽ ghi nhầm vào đêm đã qua.
     */
    private void sangDemMoi() {
        oDem.setText(kho.demNay);
        veTatCa();
    }

    /* ==================== GẮN VIEW ==================== */

    private void timView() {
        trang = findViewById(R.id.trang);
        phuDe = findViewById(R.id.phu_de);
        chipDongBo = findViewById(R.id.chip_dong_bo);
        nutTab = new LinearLayout[]{
                findViewById(R.id.tab_ghi),
                findViewById(R.id.tab_thong_ke),
                findViewById(R.id.tab_cai_dat),
                findViewById(R.id.tab_may)
        };
        hinhTab = new TextView[]{
                findViewById(R.id.tab_ghi_hinh),
                findViewById(R.id.tab_thong_ke_hinh),
                findViewById(R.id.tab_cai_dat_hinh),
                findViewById(R.id.tab_may_hinh)
        };
        chuTab = new TextView[]{
                findViewById(R.id.tab_ghi_chu),
                findViewById(R.id.tab_thong_ke_chu),
                findViewById(R.id.tab_cai_dat_chu),
                findViewById(R.id.tab_may_chu)
        };

        uiSoDu = findViewById(R.id.ui_so_du);
        uiChuoi = findViewById(R.id.ui_chuoi);
        demNhan = findViewById(R.id.dem_nhan);
        demChinh = findViewById(R.id.dem_chinh);
        demPhu = findViewById(R.id.dem_phu);
        tieuDeBuoc = findViewById(R.id.tieu_de_buoc);
        goiYBuoc = findViewById(R.id.goi_y_buoc);
        xemTruocLoi = findViewById(R.id.xem_truoc_loi);
        xemTruocThem = findViewById(R.id.xem_truoc_them);
        xemTruocTien = findViewById(R.id.xem_truoc_tien);
        dsBuoc = findViewById(R.id.ds_buoc);
        dsGanDay = findViewById(R.id.ds_gan_day);
        oDem = findViewById(R.id.o_dem);
        oGioNgu = findViewById(R.id.o_gio_ngu);
        oGioDay = findViewById(R.id.o_gio_day);

        bieuDo = findViewById(R.id.bieu_do);
        tkThuong = findViewById(R.id.tk_thuong);
        tkPhat = findViewById(R.id.tk_phat);
        dsChiSo = findViewById(R.id.ds_chi_so);
        dsLichSu = findViewById(R.id.ds_lich_su);

        oUrl = findViewById(R.id.o_url);
        oPhatVuot = findViewById(R.id.o_phat_vuot);
        oTruocGio = findViewById(R.id.o_truoc_gio);
        oMatChuoi = findViewById(R.id.o_mat_chuoi);
        oThuongThoiQuen = findViewById(R.id.o_thuong_thoi_quen);
        oNhacLuc = findViewById(R.id.o_nhac_luc);
        tinKetNoi = findViewById(R.id.tin_ket_noi);
        tinCapNhat = findViewById(R.id.tin_cap_nhat);
        nutCapNhat = findViewById(R.id.nut_cap_nhat);
        dsMoc = findViewById(R.id.ds_moc);
        dsMocChuoi = findViewById(R.id.ds_moc_chuoi);
        dsBuocSua = findViewById(R.id.ds_buoc_sua);
    }

    private void gan() {
        nutTab[0].setOnClickListener(v -> chuyenTab(TAB_GHI_NHAN));
        nutTab[1].setOnClickListener(v -> chuyenTab(TAB_THONG_KE));
        nutTab[2].setOnClickListener(v -> chuyenTab(TAB_CAI_DAT));
        nutTab[3].setOnClickListener(v -> chuyenTab(TAB_MAY));

        chipDongBo.setOnClickListener(v -> dayNhungCaiChuaGui(false));

        findViewById(R.id.nut_bay_gio).setOnClickListener(v -> dienGioBayGio());
        findViewById(R.id.nut_xac_nhan).setOnClickListener(v -> xacNhan());

        findViewById(R.id.nut_keo_ve).setOnClickListener(v -> keoVe());
        findViewById(R.id.nut_xuat_csv).setOnClickListener(v -> xuatCsv());

        findViewById(R.id.nut_kiem_tra_url).setOnClickListener(v -> kiemTraUrl());
        findViewById(R.id.nut_luu_url).setOnClickListener(v -> luuUrl());
        findViewById(R.id.nut_them_moc).setOnClickListener(v -> {
            themHangMoc(new CaiDatNgu.Muc("23:00", 0, ""));
        });
        findViewById(R.id.nut_them_moc_chuoi).setOnClickListener(v -> {
            themHangMocChuoi(new CaiDatNgu.MocChuoi(7, 100_000L));
        });
        findViewById(R.id.nut_them_buoc).setOnClickListener(v -> {
            themHangBuoc(new CaiDatNgu.Buoc("", 0));
        });
        findViewById(R.id.nut_luu_ngu).setOnClickListener(v -> luuCaiDatNgu());
        findViewById(R.id.nut_quyen_thong_bao).setOnClickListener(v -> xinQuyenThongBao());
        nutCapNhat.setOnClickListener(v -> {
            if (banMoiDangCho != null) taiBanMoi();
            else kiemTraBanMoi(false);
        });

        // Ba ô ngày giờ: bấm là mở bảng chọn của hệ thống, không bắt gõ tay.
        oDem.setOnClickListener(v -> GiaoDien.chonNgay(this, oDem.getText().toString(), gt -> {
            oDem.setText(gt);
            veXemTruoc();
        }));
        oGioNgu.setOnClickListener(v -> GiaoDien.chonGio(this, oGioNgu.getText().toString(), gt -> {
            oGioNgu.setText(gt);
            veXemTruoc();
        }));
        oGioDay.setOnClickListener(v -> GiaoDien.chonGio(this, oGioDay.getText().toString(), gt -> {
            oGioDay.setText(gt);
            veXemTruoc();
        }));

        oTruocGio.setOnClickListener(v ->
                GiaoDien.chonGio(this, oTruocGio.getText().toString(), oTruocGio::setText));
        oMatChuoi.setOnClickListener(v ->
                GiaoDien.chonGio(this, oMatChuoi.getText().toString(), oMatChuoi::setText));
        oNhacLuc.setOnClickListener(v ->
                GiaoDien.chonGio(this, oNhacLuc.getText().toString(), oNhacLuc::setText));

        TextWatcher doiGio = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { veXemTruoc(); }
        };
        oGioNgu.addTextChangedListener(doiGio);
        oGioDay.addTextChangedListener(doiGio);
    }

    private void chuyenTab(int i) {
        if (i < 0 || i >= nutTab.length) return;
        trang.setDisplayedChild(i);
        for (int k = 0; k < nutTab.length; k++) {
            int mau = Color.parseColor(k == i ? "#60A5FA" : "#94A3B8");
            chuTab[k].setTextColor(mau);
            hinhTab[k].setAlpha(k == i ? 1f : 0.5f);
        }
        if (i == TAB_THONG_KE) veThongKe();
        if (i == TAB_MAY && manMay != null) manMay.capNhat();
    }

    /* ==================== VẼ ==================== */

    private void veTatCa() {
        veGhiNhan();
        veBuoc();
        veXemTruoc();
        veGanDay();
        veThongKe();
        veChip();
        phuDe.setText(getString(R.string.phu_de_muc_tieu, kho.caiDat.truocGioNay));
    }

    private void veGhiNhan() {
        long soDu = kho.soDu();
        uiSoDu.setText(LuatGiacNgu.tien(soDu));
        uiSoDu.setTextColor(Color.parseColor(soDu >= 0 ? "#16A34A" : "#F87171"));
        uiChuoi.setText(getString(R.string.chuoi_ngay, kho.chuoiHienTai));

        if (oDem.getText().length() == 0) oDem.setText(kho.demNay);
        veDemNguoc();
    }

    private void veDemNguoc() {
        long bayGio = System.currentTimeMillis();

        // Ban ngày thì cái đêm app đang tính là đêm VỪA QUA, gọi "đêm nay" là sai.
        boolean dangDienRa = GiaoDien.demDangDienRa(bayGio);
        demNhan.setText(dangDienRa ? R.string.dem_nay : R.string.dem_qua);

        DemNgu daCo = kho.timTheoNgay(kho.demNay);
        if (daCo != null) {
            demChinh.setText(getString(R.string.da_ghi_luc, daCo.gio));
            demChinh.setTextColor(Color.parseColor("#34D399"));
            demPhu.setText(daCo.loi + "  ·  " + LuatGiacNgu.tienCoDau(daCo.tongTien));
            return;
        }

        demChinh.setTextColor(Color.parseColor("#F1F5F9"));

        // Neo mốc mục tiêu vào chính đêm này. Nếu chỉ hỏi "lần tới của 22:30"
        // thì lúc 04:00 sáng sẽ ra "còn 18 giờ nữa" cho một đêm đã kết thúc.
        long moc = LuatGiacNgu.mocMucTieu(kho.demNay, kho.caiDat.truocGioNay);
        long conLai = moc - bayGio;

        if (conLai > 0) {
            demChinh.setText(LuatGiacNgu.khoangCach(conLai));
            demPhu.setText(getString(R.string.nua_toi_gio, kho.caiDat.truocGioNay));
        } else if (dangDienRa) {
            demChinh.setText(getString(R.string.muon_bao_lau, LuatGiacNgu.khoangCach(-conLai)));
            demChinh.setTextColor(Color.parseColor("#FB7185"));
            demPhu.setText(getString(R.string.muon_hon_gio, kho.caiDat.truocGioNay));
        } else {
            demChinh.setText(R.string.chua_ghi);
            demChinh.setTextColor(Color.parseColor("#FBBF24"));
            demPhu.setText(R.string.ghi_bu_di);
        }
    }

    private void veBuoc() {
        dsBuoc.removeAllViews();
        List<CaiDatNgu.Buoc> buoc = kho.caiDat.cacBuoc;
        if (buoc.isEmpty()) {
            tieuDeBuoc.setText(R.string.chuan_bi_ngu);
            goiYBuoc.setText(R.string.chua_co_buoc);
            return;
        }

        for (int i = 0; i < buoc.size(); i++) {
            final int chiSo = i;
            CaiDatNgu.Buoc b = buoc.get(i);
            CheckBox o = new CheckBox(this);
            o.setText(b.nhan + (b.phut > 0 ? "   " + b.phut + "'" : ""));
            o.setTextColor(Color.parseColor("#F1F5F9"));
            o.setTextSize(15);
            o.setPadding(dp(8), dp(7), 0, dp(7));
            o.setChecked(kho.daTich.contains(i));
            o.setOnCheckedChangeListener((v, tich) -> {
                if (tich) kho.daTich.add(chiSo); else kho.daTich.remove(chiSo);
                kho.luuDemNay();
                capNhatGoiYBuoc();
                veXemTruoc();
            });
            dsBuoc.addView(o);
        }
        capNhatGoiYBuoc();
    }

    private void capNhatGoiYBuoc() {
        int tong = kho.caiDat.cacBuoc.size();
        int xong = kho.daTich.size();
        tieuDeBuoc.setText(getString(R.string.chuan_bi_ngu_dem, xong, tong));
        if (xong >= tong && tong > 0) {
            goiYBuoc.setText(getString(R.string.goi_y_buoc_xong,
                    LuatGiacNgu.tien(kho.caiDat.thuongThoiQuen)));
        } else {
            int phut = 0;
            for (CaiDatNgu.Buoc b : kho.caiDat.cacBuoc) phut += b.phut;
            goiYBuoc.setText(getString(R.string.goi_y_buoc_chua_xong, tong, phut,
                    LuatGiacNgu.tien(kho.caiDat.thuongThoiQuen)));
        }
    }

    private void veXemTruoc() {
        String gio = oGioNgu.getText().toString().trim();
        LuatGiacNgu.KetQua kq = gio.isEmpty() ? null : LuatGiacNgu.danhGia(gio, kho.caiDat);
        if (kq == null) {
            xemTruocLoi.setText(R.string.chon_gio_de_xem);
            xemTruocThem.setText("");
            xemTruocTien.setText("--");
            xemTruocTien.setTextColor(Color.parseColor("#F8FAFC"));
            return;
        }

        int soBuoc = kho.caiDat.cacBuoc.size();
        long thuongThoiQuen = (soBuoc > 0 && kho.daTich.size() >= soBuoc)
                ? kho.caiDat.thuongThoiQuen : 0;

        int chuoiMoi = "cong".equals(kq.viec) ? kho.chuoiHienTai + 1
                : "xoa".equals(kq.viec) ? 0 : kho.chuoiHienTai;
        long thuongChuoi = "cong".equals(kq.viec)
                ? LuatGiacNgu.thuongMocChuoi(chuoiMoi, kho.caiDat) : 0;
        long tong = kq.tien + thuongChuoi + thuongThoiQuen;

        xemTruocLoi.setText(kq.loi);

        StringBuilder them = new StringBuilder();
        them.append(getString(R.string.chuoi_sau_khi_ghi, chuoiMoi));
        if (thuongChuoi != 0) {
            them.append(" · ").append(getString(R.string.thuong_them,
                    LuatGiacNgu.loiMocChuoi(chuoiMoi, kho.caiDat),
                    LuatGiacNgu.tien(thuongChuoi)));
        }
        if (thuongThoiQuen != 0) {
            them.append(" · ").append(getString(R.string.thoi_quen_them,
                    LuatGiacNgu.tien(thuongThoiQuen)));
        }
        xemTruocThem.setText(them.toString());

        xemTruocTien.setText(LuatGiacNgu.tienCoDau(tong));
        xemTruocTien.setTextColor(Color.parseColor(
                tong > 0 ? "#34D399" : tong < 0 ? "#F87171" : "#94A3B8"));
    }

    private void veGanDay() {
        dsGanDay.removeAllViews();
        if (kho.danhSach.isEmpty()) {
            dsGanDay.addView(chuMo(getString(R.string.chua_ghi_dem_nao)));
            return;
        }
        int n = Math.min(5, kho.danhSach.size());
        for (int i = 0; i < n; i++) dsGanDay.addView(theDem(kho.danhSach.get(i)));
    }

    private void veThongKe() {
        if (bieuDo == null) return;

        // Biểu đồ 14 đêm gần nhất, tính từ "đêm" hiện tại lùi về trước.
        List<BieuDo.Cot> cot = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < LuatGiacNgu.MOC_CHIA_NGAY) {
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        c.add(Calendar.DAY_OF_MONTH, -13);
        for (int i = 0; i < 14; i++) {
            String iso = LuatGiacNgu.ngayISO(c);
            DemNgu d = kho.timTheoNgay(iso);
            String nhan = iso.substring(8);
            if (d == null) cot.add(new BieuDo.Cot(nhan, 0, false, false));
            else cot.add(new BieuDo.Cot(nhan, d.giaTri, d.tre, true));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        bieuDo.datDuLieu(cot);

        Calendar nay = Calendar.getInstance();
        String thang = nay.get(Calendar.YEAR) + "-" + LuatGiacNgu.hai(nay.get(Calendar.MONTH) + 1);
        long thuong = 0, phat = 0;
        for (DemNgu d : kho.danhSach) {
            if (!d.ngay.startsWith(thang)) continue;
            if (d.tongTien > 0) thuong += d.tongTien; else phat += Math.abs(d.tongTien);
        }
        tkThuong.setText("+" + LuatGiacNgu.tien(thuong));
        tkPhat.setText("-" + LuatGiacNgu.tien(phat));

        int tong = kho.danhSach.size();
        int dungGio = 0;
        double tongGiaTri = 0; int demGiaTri = 0;
        double tongThoiLuong = 0; int demThoiLuong = 0;
        for (DemNgu d : kho.danhSach) {
            if (!d.tre) dungGio++;
            if (!Double.isNaN(d.giaTri)) { tongGiaTri += d.giaTri; demGiaTri++; }
            if (d.thoiLuong != null) { tongThoiLuong += d.thoiLuong; demThoiLuong++; }
        }
        String gioTB = demGiaTri > 0 ? LuatGiacNgu.giaTriRaGio(tongGiaTri / demGiaTri) : "--:--";
        String thoiLuongTB = demThoiLuong > 0
                ? String.format(Locale.US, "%.1f giờ", tongThoiLuong / demThoiLuong) : "--";

        long soDu = kho.soDu();
        dsChiSo.removeAllViews();
        dsChiSo.addView(hangChiSo("Số đêm đã ghi", String.valueOf(tong), null));
        dsChiSo.addView(hangChiSo("Tỉ lệ đúng giờ",
                (tong > 0 ? Math.round(dungGio * 100f / tong) : 0) + "%", null));
        dsChiSo.addView(hangChiSo("Giờ ngủ trung bình", gioTB, null));
        dsChiSo.addView(hangChiSo("Thời lượng ngủ TB", thoiLuongTB, null));
        dsChiSo.addView(hangChiSo("Chuỗi dài nhất",
                getString(R.string.chuoi_ngay, kho.chuoiDaiNhat), null));
        dsChiSo.addView(hangChiSo("Chuỗi hiện tại",
                getString(R.string.chuoi_ngay, kho.chuoiHienTai), "#FBBF24"));
        dsChiSo.addView(hangChiSo("Tổng quỹ", LuatGiacNgu.tien(soDu),
                soDu >= 0 ? "#34D399" : "#FB7185"));

        dsLichSu.removeAllViews();
        if (kho.danhSach.isEmpty()) {
            dsLichSu.addView(chuMo(getString(R.string.chua_ghi_dem_nao)));
        } else {
            for (DemNgu d : kho.danhSach) dsLichSu.addView(theDem(d));
        }
    }

    private void veChip() {
        if (dangGui) { datChip(getString(R.string.dang_gui), "#FBBF24"); return; }
        if (!DongBo.urlHopLe(kho.caiDat.urlWebApp)) {
            datChip(getString(R.string.chua_ket_noi), "#94A3B8");
            return;
        }
        int cho = kho.chuaGui().size();
        if (cho > 0) datChip(getString(R.string.cho_gui, cho), "#FBBF24");
        else datChip(getString(R.string.da_dong_bo), "#34D399");
    }

    private void datChip(String chu, String mau) {
        chipDongBo.setText(chu);
        chipDongBo.setTextColor(Color.parseColor(mau));
    }

    /* ==================== THẺ MỘT ĐÊM ==================== */

    /** Một dòng "nhãn ......... giá trị" trong bảng Tổng quan. */
    private View hangChiSo(String nhan, String giaTri, String mau) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, dp(9), 0, dp(9));

        TextView t = new TextView(this);
        t.setText(nhan);
        t.setTextColor(Color.parseColor("#94A3B8"));
        t.setTextSize(14);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        h.addView(t);

        TextView p = new TextView(this);
        p.setText(giaTri);
        p.setTextColor(Color.parseColor(mau == null ? "#F1F5F9" : mau));
        p.setTextSize(15);
        p.setTypeface(null, android.graphics.Typeface.BOLD);
        h.addView(p);
        return h;
    }

    private TextView chuMo(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#64748B"));
        t.setTextSize(13);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private View theDem(final DemNgu d) {
        LinearLayout the = new LinearLayout(this);
        the.setOrientation(LinearLayout.VERTICAL);
        // Vạch màu bên trái cho biết đêm đó đúng giờ hay trễ, giống bản PWA.
        the.setBackgroundResource(d.tre ? R.drawable.vien_trai_do : R.drawable.vien_trai_xanh_la);
        the.setPadding(dp(14), dp(11), dp(13), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        the.setLayoutParams(lp);

        LinearLayout dong = new LinearLayout(this);
        dong.setOrientation(LinearLayout.HORIZONTAL);
        dong.setGravity(Gravity.CENTER_VERTICAL);

        TextView gio = new TextView(this);
        gio.setText(d.gio);
        gio.setTextColor(Color.parseColor("#F1F5F9"));
        gio.setTextSize(19);
        gio.setTypeface(null, android.graphics.Typeface.BOLD);
        dong.addView(gio);

        TextView ngay = new TextView(this);
        ngay.setText("  " + LuatGiacNgu.ngayNgan(d.ngay)
                + (d.gioDay.isEmpty() ? "" : "  →  " + d.gioDay));
        ngay.setTextColor(Color.parseColor("#64748B"));
        ngay.setTextSize(13);
        ngay.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dong.addView(ngay);

        TextView phai = new TextView(this);
        phai.setText(LuatGiacNgu.tienCoDau(d.tongTien));
        phai.setTextColor(Color.parseColor(
                d.tongTien > 0 ? "#34D399" : d.tongTien < 0 ? "#FB7185" : "#94A3B8"));
        phai.setTextSize(16);
        phai.setTypeface(null, android.graphics.Typeface.BOLD);
        dong.addView(phai);
        the.addView(dong);

        TextView duoi = new TextView(this);
        StringBuilder s = new StringBuilder(d.loi == null ? "" : d.loi);
        s.append("  ·  chuỗi ").append(d.chuoiSauDem);
        if (d.thoiLuong != null) s.append("  ·  ngủ ")
                .append(String.format(Locale.US, "%.1f", d.thoiLuong)).append(" giờ");
        if (!d.daGui) s.append("  ·  chờ gửi");
        duoi.setText(s.toString());
        duoi.setTextColor(Color.parseColor("#94A3B8"));
        duoi.setTextSize(13);
        duoi.setPadding(0, dp(3), 0, 0);
        the.addView(duoi);

        the.setOnClickListener(v -> {
            oDem.setText(d.ngay);
            oGioNgu.setText(d.gio);
            oGioDay.setText(d.gioDay);
            chuyenTab(TAB_GHI_NHAN);
        });
        the.setOnLongClickListener(v -> { hoiXoaDem(d.ngay); return true; });
        return the;
    }

    /* ==================== HÀNH ĐỘNG ==================== */

    private void dienGioBayGio() {
        oDem.setText(kho.demNay);
        oGioNgu.setText(LuatGiacNgu.gioHienTai(System.currentTimeMillis()));
        veXemTruoc();
    }

    private void xacNhan() {
        final String dem = oDem.getText().toString().trim();
        // Bình thường giờ đến từ bảng chọn nên đã đúng dạng. Vẫn chuẩn hoá cho
        // chắc, phòng khi giá trị đến từ nơi khác: "1030" cũng thành "10:30".
        final String gio = GiaoDien.chuanHoaGio(oGioNgu.getText().toString());
        final String gioDay = GiaoDien.chuanHoaGio(oGioDay.getText().toString());

        if (dem.isEmpty() || gio.isEmpty()) { bao(getString(R.string.thieu_du_lieu)); return; }
        if (!dem.matches("^\\d{4}-\\d{2}-\\d{2}$")) { bao(getString(R.string.dem_sai)); return; }
        if (!CauHinh.laGio(gio)) { bao(getString(R.string.gio_sai)); return; }
        if (!gioDay.isEmpty() && !CauHinh.laGio(gioDay)) { bao(getString(R.string.gio_sai)); return; }

        DemNgu daCo = kho.timTheoNgay(dem);
        if (daCo != null) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.ghi_de_dem, LuatGiacNgu.ngayNgan(dem), daCo.gio))
                    .setPositiveButton(android.R.string.ok, (d, w) -> ghiThat(dem, gio, gioDay))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        ghiThat(dem, gio, gioDay);
    }

    private void ghiThat(String dem, String gio, String gioDay) {
        // Số bước đã tích chỉ tính cho đúng đêm nay; ghi bù đêm cũ thì giữ số cũ.
        int soBuoc;
        if (dem.equals(kho.demNay)) {
            soBuoc = kho.daTich.size();
        } else {
            DemNgu cu = kho.timTheoNgay(dem);
            soBuoc = cu != null ? cu.soBuocXong : 0;
        }

        DemNgu d = kho.ghiDem(dem, gio, gioDay, soBuoc);
        veTatCa();
        if (d != null) {
            bao((d.tongTien >= 0 ? getString(R.string.da_ghi_nhan) : getString(R.string.da_ghi_bi_phat))
                    + ": " + getString(R.string.ket_qua_dem,
                    LuatGiacNgu.tienCoDau(d.tongTien), d.chuoiSauDem));
        }
        dayNhungCaiChuaGui(true);
    }

    private void hoiXoaDem(final String ngay) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.xoa_dem_hoi, LuatGiacNgu.ngayNgan(ngay)))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    kho.xoaDem(ngay);
                    veTatCa();
                    bao(getString(R.string.da_xoa_dem, LuatGiacNgu.ngayNgan(ngay)));
                    if (DongBo.urlHopLe(kho.caiDat.urlWebApp)) {
                        DongBo.xoa(kho.caiDat.urlWebApp, ngay, new DongBo.Xong<Integer>() {
                            @Override public void thanhCong(Integer k) { }
                            @Override
                            public void thatBai(String loi) {
                                // Xoá hụt mà im lặng thì lần "Tải từ Sheet" sau
                                // đêm đó sống lại, người dùng không hiểu vì sao.
                                bao(getString(R.string.xoa_sheet_that_bai,
                                        LuatGiacNgu.ngayNgan(ngay), loi));
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /* ==================== ĐỒNG BỘ ==================== */

    private void dayNhungCaiChuaGui(final boolean lang) {
        if (dangGui) return;
        final List<DemNgu> can = kho.chuaGui();
        if (can.isEmpty()) { veChip(); return; }
        if (!DongBo.urlHopLe(kho.caiDat.urlWebApp)) {
            if (!lang) bao(getString(R.string.chua_dat_url));
            veChip();
            return;
        }

        dangGui = true;
        veChip();
        DongBo.day(kho.caiDat.urlWebApp, can, new DongBo.Xong<Integer>() {
            @Override
            public void thanhCong(Integer soDaLuu) {
                for (DemNgu d : can) { d.daGui = true; d.daXacNhan = true; }
                kho.luuDanhSach();
                dangGui = false;
                veChip();
                if (!lang) bao(getString(R.string.da_dong_bo) + ": " + can.size() + " đêm");
            }

            @Override
            public void thatBai(String loi) {
                dangGui = false;
                datChip(getString(R.string.loi_gui), "#F87171");
                if (!lang) bao(loi);
            }
        });
    }

    private void keoVe() {
        if (!DongBo.urlHopLe(kho.caiDat.urlWebApp)) { bao(getString(R.string.chua_dat_url)); return; }
        datChip(getString(R.string.dang_gui), "#FBBF24");
        DongBo.keoVe(kho.caiDat.urlWebApp, new DongBo.Xong<List<DemNgu>>() {
            @Override
            public void thanhCong(List<DemNgu> ds) {
                int them = kho.tronTuSheet(ds);
                veTatCa();
                bao(getString(R.string.da_keo_ve, them));
            }

            @Override
            public void thatBai(String loi) {
                datChip(getString(R.string.loi_gui), "#F87171");
                bao(loi);
            }
        });
    }

    private void kiemTraUrl() {
        String url = oUrl.getText().toString().trim();
        if (!DongBo.urlHopLe(url)) { tinKetNoi.setText(R.string.url_sai_dinh_dang); return; }
        tinKetNoi.setText(R.string.dang_kiem_tra);
        DongBo.kiemTra(url, new DongBo.Xong<String>() {
            @Override public void thanhCong(String mo) { tinKetNoi.setText(mo); }
            @Override public void thatBai(String loi) { tinKetNoi.setText(loi); }
        });
    }

    private void luuUrl() {
        String url = oUrl.getText().toString().trim();
        if (!url.isEmpty() && !DongBo.urlHopLe(url)) {
            tinKetNoi.setText(R.string.url_sai_dinh_dang);
            return;
        }
        kho.caiDat.urlWebApp = url;
        kho.luuCaiDat();
        veChip();
        bao(getString(R.string.da_luu_cai_dat));
        dayNhungCaiChuaGui(false);
    }

    private void xuatCsv() {
        StringBuilder sb = new StringBuilder(
                "ngay,gio_len_giuong,gio_thuc_day,thoi_luong,tien_goc,thuong_chuoi,"
                        + "thuong_thoi_quen,tong_tien,chuoi,ly_do\n");
        List<DemNgu> tang = new ArrayList<>(kho.danhSach);
        java.util.Collections.reverse(tang);
        for (DemNgu d : tang) {
            sb.append(d.ngay).append(',').append(d.gio).append(',').append(d.gioDay).append(',')
              .append(d.thoiLuong == null ? "" : d.thoiLuong).append(',')
              .append(d.tienGoc).append(',').append(d.thuongChuoi).append(',')
              .append(d.thuongThoiQuen).append(',').append(d.tongTien).append(',')
              .append(d.chuoiSauDem).append(',')
              .append('"').append(d.loi == null ? "" : d.loi.replace('"', '\'')).append('"')
              .append('\n');
        }
        Intent chia = new Intent(Intent.ACTION_SEND);
        chia.setType("text/plain");
        chia.putExtra(Intent.EXTRA_SUBJECT, "Thoi gian bieu - giac ngu.csv");
        chia.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(chia, getString(R.string.nut_xuat_csv)));
    }

    /* ==================== CÀI ĐẶT GIẤC NGỦ ==================== */

    private static class HangMoc {
        View goc; EditText den, tien, loi;
    }

    private static class HangMocChuoi {
        View goc; EditText ngay, tien;
    }

    private static class HangBuoc {
        View goc; EditText nhan, phut;
    }

    private void napCaiDatVaoO() {
        CaiDatNgu c = kho.caiDat;
        oUrl.setText(c.urlWebApp);
        oPhatVuot.setText(String.valueOf(c.phatMoiGioVuot));
        oTruocGio.setText(c.truocGioNay);
        oMatChuoi.setText(c.matChuoiSau);
        oThuongThoiQuen.setText(String.valueOf(c.thuongThoiQuen));
        oNhacLuc.setText(c.nhacLuc);

        dsMoc.removeAllViews(); hangMoc.clear();
        for (CaiDatNgu.Muc m : c.cacMuc) themHangMoc(m);

        dsMocChuoi.removeAllViews(); hangMocChuoi.clear();
        for (CaiDatNgu.MocChuoi m : c.cacMocChuoi) themHangMocChuoi(m);

        dsBuocSua.removeAllViews(); hangBuoc.clear();
        for (CaiDatNgu.Buoc b : c.cacBuoc) themHangBuoc(b);
    }

    private EditText oNho(String gtri, int kieu, float can) {
        EditText o = new EditText(this);
        o.setText(gtri);
        o.setInputType(kieu);
        o.setTextSize(14);
        o.setSingleLine(true);
        o.setTextColor(Color.parseColor("#F1F5F9"));
        o.setBackgroundResource(R.drawable.o_nhap);
        o.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, can);
        lp.rightMargin = dp(6);
        o.setLayoutParams(lp);
        return o;
    }

    private Button nutXoaHang() {
        Button b = new Button(this);
        b.setText("✕");
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#FB7185"));
        b.setBackgroundResource(R.drawable.nut_nguy);
        b.setStateListAnimator(null);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(42));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout hangNgang() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        h.setLayoutParams(lp);
        return h;
    }

    private void themHangMoc(CaiDatNgu.Muc m) {
        final HangMoc h = new HangMoc();
        LinearLayout hang = hangNgang();
        h.den = oNho(m.den, android.text.InputType.TYPE_CLASS_DATETIME, 1.1f);
        h.tien = oNho(String.valueOf(m.tien),
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, 1.4f);
        h.loi = oNho(m.loi == null ? "" : m.loi, android.text.InputType.TYPE_CLASS_TEXT, 2.2f);
        Button xoa = nutXoaHang();
        hang.addView(h.den); hang.addView(h.tien); hang.addView(h.loi); hang.addView(xoa);
        h.goc = hang;
        xoa.setOnClickListener(v -> { dsMoc.removeView(hang); hangMoc.remove(h); });
        dsMoc.addView(hang);
        hangMoc.add(h);
    }

    private void themHangMocChuoi(CaiDatNgu.MocChuoi m) {
        final HangMocChuoi h = new HangMocChuoi();
        LinearLayout hang = hangNgang();
        h.ngay = oNho(String.valueOf(m.ngay), android.text.InputType.TYPE_CLASS_NUMBER, 1f);
        h.tien = oNho(String.valueOf(m.tien), android.text.InputType.TYPE_CLASS_NUMBER, 2f);
        Button xoa = nutXoaHang();
        hang.addView(h.ngay); hang.addView(h.tien); hang.addView(xoa);
        h.goc = hang;
        xoa.setOnClickListener(v -> { dsMocChuoi.removeView(hang); hangMocChuoi.remove(h); });
        dsMocChuoi.addView(hang);
        hangMocChuoi.add(h);
    }

    private void themHangBuoc(CaiDatNgu.Buoc b) {
        final HangBuoc h = new HangBuoc();
        LinearLayout hang = hangNgang();
        h.nhan = oNho(b.nhan, android.text.InputType.TYPE_CLASS_TEXT, 3f);
        h.phut = oNho(String.valueOf(b.phut), android.text.InputType.TYPE_CLASS_NUMBER, 1f);
        Button xoa = nutXoaHang();
        hang.addView(h.nhan); hang.addView(h.phut); hang.addView(xoa);
        h.goc = hang;
        xoa.setOnClickListener(v -> { dsBuocSua.removeView(hang); hangBuoc.remove(h); });
        dsBuocSua.addView(hang);
        hangBuoc.add(h);
    }

    private void luuCaiDatNgu() {
        List<String> loi = new ArrayList<>();
        CaiDatNgu c = new CaiDatNgu();
        c.urlWebApp = oUrl.getText().toString().trim();
        // Nút "Lưu URL" đã kiểm, nhưng nút này cũng ghi đè URL — gõ nhầm mà
        // không ai kêu thì đồng bộ chết lặng lẽ.
        if (!c.urlWebApp.isEmpty() && !DongBo.urlHopLe(c.urlWebApp)) {
            loi.add(getString(R.string.url_sai_dinh_dang));
        }

        c.phatMoiGioVuot = soNguyen(oPhatVuot, 0);
        c.truocGioNay = oTruocGio.getText().toString().trim();
        c.matChuoiSau = oMatChuoi.getText().toString().trim();
        c.thuongThoiQuen = soNguyen(oThuongThoiQuen, 0);
        c.nhacLuc = oNhacLuc.getText().toString().trim();

        if (!laGioMoc(c.truocGioNay)) loi.add("\"Ngủ trước\" phải dạng HH:mm.");
        if (!laGioMoc(c.matChuoiSau)) loi.add("\"Muộn hơn\" phải dạng HH:mm.");
        if (!CauHinh.laGio(c.nhacLuc)) loi.add("Giờ nhắc phải dạng HH:mm.");

        c.cacMuc = new ArrayList<>();
        for (HangMoc h : hangMoc) {
            String den = h.den.getText().toString().trim();
            if (den.isEmpty()) continue;
            if (!laGioMoc(den)) { loi.add("Mốc \"" + den + "\" phải dạng HH:mm (dùng 24:00-27:00 cho sau nửa đêm)."); continue; }
            c.cacMuc.add(new CaiDatNgu.Muc(den, soNguyen(h.tien, 0), h.loi.getText().toString().trim()));
        }
        if (c.cacMuc.isEmpty()) loi.add("Phải có ít nhất một mốc thưởng phạt.");

        c.cacMocChuoi = new ArrayList<>();
        for (HangMocChuoi h : hangMocChuoi) {
            int ngay = (int) soNguyen(h.ngay, 0);
            if (ngay <= 0) continue;
            c.cacMocChuoi.add(new CaiDatNgu.MocChuoi(ngay, soNguyen(h.tien, 0)));
        }

        c.cacBuoc = new ArrayList<>();
        for (HangBuoc h : hangBuoc) {
            String nhan = h.nhan.getText().toString().trim();
            if (nhan.isEmpty()) continue;
            c.cacBuoc.add(new CaiDatNgu.Buoc(nhan, (int) soNguyen(h.phut, 0)));
        }

        if (!loi.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.chua_luu_duoc)
                    .setMessage(String.join("\n\n", loi))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        c.phutChuanBi = kho.caiDat.phutChuanBi;
        kho.caiDat = c;
        kho.luuCaiDat();

        // Xoá bớt bước mà vẫn giữ dấu tích cũ thì checklist hiện "(5/3)" và
        // thưởng thói quen được trao oan.
        kho.cheoLaiDauTich();
        // Đổi mốc tiền thì mọi đêm cũ đổi kết quả theo, và phải gửi lại lên Sheet.
        kho.tinhLai();
        kho.luuDanhSach();

        NhacNgu.datLai(this);
        veTatCa();
        bao(getString(R.string.da_luu_cai_dat));
        dayNhungCaiChuaGui(true);
    }

    /** Mốc cấu hình cho phép 24:00 đến 27:00 để chỉ giờ sau nửa đêm. */
    private boolean laGioMoc(String s) {
        if (s == null) return false;
        if (!s.matches("^\\d{1,2}:[0-5][0-9]$")) return false;
        int h = Integer.parseInt(s.split(":")[0]);
        return h >= 0 && h <= 27;
    }

    private long soNguyen(EditText o, long macDinh) {
        try {
            return Long.parseLong(o.getText().toString().trim());
        } catch (NumberFormatException e) {
            return macDinh;
        }
    }

    /* ==================== TỰ CẬP NHẬT ==================== */

    private void kiemTraBanMoi(final boolean lang) {
        if (!lang) tinCapNhat.setText(R.string.dang_kiem_tra_cn);
        CapNhat.kiemTra(this, new CapNhat.Xong() {
            @Override
            public void xong(CapNhat.BanMoi b) {
                banMoiDangCho = b;
                if (b == null) {
                    tinCapNhat.setText(getString(R.string.phien_ban_dang_dung,
                            CapNhat.tenPhienBanHienTai(ChinhActivity.this))
                            + "  ·  " + getString(R.string.da_moi_nhat));
                    nutCapNhat.setText(R.string.nut_kiem_tra_cap_nhat);
                    return;
                }
                tinCapNhat.setText(getString(R.string.co_ban_moi, b.tenPhienBan, b.ghiChu));
                nutCapNhat.setText(getString(R.string.nut_tai_cai, b.tenPhienBan));
                if (lang) bao(getString(R.string.co_ban_moi, b.tenPhienBan, ""));
            }

            @Override
            public void hong(String loi) {
                if (!lang) tinCapNhat.setText(getString(R.string.loi_cap_nhat, loi));
            }
        });
    }

    private void taiBanMoi() {
        if (banMoiDangCho == null) return;
        nutCapNhat.setEnabled(false);
        CapNhat.taiVaCai(this, banMoiDangCho,
                p -> tinCapNhat.setText(getString(R.string.dang_tai_cn, p)),
                new CapNhat.Xong() {
                    @Override
                    public void xong(CapNhat.BanMoi b) {
                        nutCapNhat.setEnabled(true);
                        tinCapNhat.setText(getString(R.string.co_ban_moi,
                                b.tenPhienBan, b.ghiChu));
                    }

                    @Override
                    public void hong(String loi) {
                        nutCapNhat.setEnabled(true);
                        tinCapNhat.setText(getString(R.string.loi_cap_nhat, loi));
                    }
                });
    }

    /* ==================== LẶT VẶT ==================== */

    private void khoiDongDichVuKhoa() {
        try {
            Intent i = new Intent(this, DichVuKhoa.class);
            i.setAction(DichVuKhoa.HANH_DONG_CANH_GIU);
            startForegroundService(i);
        } catch (Exception e) {
            NhatKy.ghi(this, "loi-dung-dich-vu", String.valueOf(e.getMessage()));
        }
    }

    private void xinQuyenThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void bao(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
