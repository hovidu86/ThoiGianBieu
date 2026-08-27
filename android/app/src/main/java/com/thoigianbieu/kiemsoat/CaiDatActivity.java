package com.thoigianbieu.kiemsoat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Màn hình cài đặt, cũng là màn hình chính của app. */
public class CaiDatActivity extends Activity {

    private CauHinh ch;
    private EditText oGioKhoa, oGioKetThuc, oLapLai, oCanhBao, oKhoangCach, oMa, oMa2;
    private EditText oNqPhutDung, oNqPhutNghi, oNqPhutReset, oNqCanhBao,
            oNqTuGio, oNqDenGio, oNqKhanCap;
    private CheckBox chkNqBat, chkNqKhongKhoaKhiGoi;
    private TextView tinhTrang;

    @Override
    protected void onCreate(Bundle luuTruoc) {
        super.onCreate(luuTruoc);
        setContentView(R.layout.man_cai_dat);
        ch = new CauHinh(this);

        oGioKhoa = findViewById(R.id.o_gio_khoa);
        oGioKetThuc = findViewById(R.id.o_gio_ket_thuc);
        oLapLai = findViewById(R.id.o_lap_lai);
        oCanhBao = findViewById(R.id.o_canh_bao);
        oKhoangCach = findViewById(R.id.o_khoang_cach);
        oMa = findViewById(R.id.o_ma);
        oMa2 = findViewById(R.id.o_ma_2);
        tinhTrang = findViewById(R.id.tinh_trang);

        oNqPhutDung = findViewById(R.id.o_nq_phut_dung);
        oNqPhutNghi = findViewById(R.id.o_nq_phut_nghi);
        oNqPhutReset = findViewById(R.id.o_nq_phut_reset);
        oNqCanhBao = findViewById(R.id.o_nq_canh_bao);
        oNqTuGio = findViewById(R.id.o_nq_tu_gio);
        oNqDenGio = findViewById(R.id.o_nq_den_gio);
        oNqKhanCap = findViewById(R.id.o_nq_khan_cap);
        chkNqBat = findViewById(R.id.chk_nq_bat);
        chkNqKhongKhoaKhiGoi = findViewById(R.id.chk_nq_khong_khoa_khi_goi);

        oGioKhoa.setText(ch.gioKhoa());
        oGioKetThuc.setText(ch.gioKetThuc());
        oLapLai.setText(String.valueOf(ch.lapLaiPhut()));
        oCanhBao.setText(ch.canhBaoPhut());
        oKhoangCach.setText(String.valueOf(ch.khoangCachGiay()));

        chkNqBat.setChecked(ch.ngatQuangBat());
        oNqPhutDung.setText(String.valueOf(ch.nqPhutDung()));
        oNqPhutNghi.setText(String.valueOf(ch.nqPhutNghi()));
        oNqPhutReset.setText(String.valueOf(ch.nqPhutReset()));
        oNqCanhBao.setText(String.valueOf(ch.nqCanhBaoPhut()));
        oNqTuGio.setText(ch.nqTuGio());
        oNqDenGio.setText(ch.nqDenGio());
        oNqKhanCap.setText(String.valueOf(ch.nqKhanCapMoiNgay()));
        chkNqKhongKhoaKhiGoi.setChecked(ch.nqKhongKhoaKhiGoi());

        ((Button) findViewById(R.id.nut_luu)).setOnClickListener(v -> luu());
        ((Button) findViewById(R.id.nut_thong_ke)).setOnClickListener(v -> xemThongKe());
        ((Button) findViewById(R.id.nut_quyen_quan_tri)).setOnClickListener(v -> xinQuyenQuanTri());
        ((Button) findViewById(R.id.nut_quyen_lop_phu)).setOnClickListener(v -> xinQuyenLopPhu());
        ((Button) findViewById(R.id.nut_quyen_pin)).setOnClickListener(v -> xinBoToiUuPin());
        ((Button) findViewById(R.id.nut_khoa_thu)).setOnClickListener(v -> khoaThu());
        ((Button) findViewById(R.id.nut_nhat_ky)).setOnClickListener(v -> xemNhatKy());

        xinQuyenThongBao();
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatTinhTrang();
    }

    /* ==================== LƯU ==================== */

    private void luu() {
        List<String> loi = new ArrayList<>();

        String gioKhoa = oGioKhoa.getText().toString().trim();
        String gioKet = oGioKetThuc.getText().toString().trim();
        String lapLai = oLapLai.getText().toString().trim();
        String canhBao = oCanhBao.getText().toString().trim();
        String khoang = oKhoangCach.getText().toString().trim();
        String ma = oMa.getText().toString();
        String ma2 = oMa2.getText().toString();

        if (!CauHinh.laGio(gioKhoa)) loi.add(getString(R.string.loi_gio_khoa));
        if (!CauHinh.laGio(gioKet)) loi.add(getString(R.string.loi_gio_ket_thuc));
        if (!laSoDuong(lapLai)) loi.add(getString(R.string.loi_lap_lai));
        if (!laSoDuong(khoang)) loi.add(getString(R.string.loi_khoang_cach));
        if (!canhBao.matches("^[0-9]+( *, *[0-9]+)*$")) loi.add(getString(R.string.loi_canh_bao));

        // --- dùng ngắt quãng ---
        String nqDung = oNqPhutDung.getText().toString().trim();
        String nqNghi = oNqPhutNghi.getText().toString().trim();
        String nqReset = oNqPhutReset.getText().toString().trim();
        String nqCanhBao = oNqCanhBao.getText().toString().trim();
        String nqTu = oNqTuGio.getText().toString().trim();
        String nqDen = oNqDenGio.getText().toString().trim();
        String nqKhanCap = oNqKhanCap.getText().toString().trim();

        if (!laSoDuong(nqDung)) loi.add(getString(R.string.loi_nq_phut_dung));
        if (!laSoDuong(nqNghi)) loi.add(getString(R.string.loi_nq_phut_nghi));
        if (!laSoDuong(nqReset)) loi.add(getString(R.string.loi_nq_phut_reset));
        if (!CauHinh.laGio(nqTu) || !CauHinh.laGio(nqDen)) loi.add(getString(R.string.loi_nq_gio));
        if (!laSoKhongAm(nqKhanCap)) loi.add(getString(R.string.loi_nq_khan_cap));
        // Cảnh báo phải nằm trong đợt, nếu không thì cảnh báo nổ ngay lúc bắt đầu.
        if (!laSoKhongAm(nqCanhBao)
                || (laSoDuong(nqDung) && Integer.parseInt(nqCanhBao) >= Integer.parseInt(nqDung))) {
            loi.add(getString(R.string.loi_nq_canh_bao));
        }

        boolean doiMa = !ma.isEmpty() || !ma2.isEmpty() || !ch.daDatMa();
        if (doiMa) {
            if (ma.length() < CauHinh.DO_DAI_MA_TOI_THIEU) {
                loi.add(getString(R.string.loi_ma_ngan, CauHinh.DO_DAI_MA_TOI_THIEU));
            } else if (!ma.equals(ma2)) {
                loi.add(getString(R.string.loi_ma_khong_khop));
            }
        }

        if (!loi.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.chua_luu_duoc)
                    .setMessage(String.join("\n\n", loi))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ch.luu(gioKhoa, gioKet, Integer.parseInt(lapLai), canhBao, Integer.parseInt(khoang));
        ch.luuNgatQuang(chkNqBat.isChecked(),
                Integer.parseInt(nqDung), Integer.parseInt(nqNghi), Integer.parseInt(nqReset),
                Integer.parseInt(nqCanhBao), nqTu, nqDen,
                Integer.parseInt(nqKhanCap), chkNqKhongKhoaKhiGoi.isChecked());
        if (doiMa) {
            ch.datMa(ma);
            oMa.setText("");
            oMa2.setText("");
        }

        // Lịch cũ không còn đúng nữa, tính lại từ đầu.
        long bayGio = System.currentTimeMillis();
        ch.datMocKhoa(ch.trongKhoangKhoa(bayGio) ? bayGio + 60_000L : ch.mocKhoaDauTien(bayGio));
        LenLich.datLai(this);
        khoiDongDichVu();

        Toast.makeText(this, getString(R.string.da_luu, LenLich.gioPhut(ch.mocKhoa())),
                Toast.LENGTH_LONG).show();
        capNhatTinhTrang();
    }

    private boolean laSoDuong(String s) {
        try {
            return Integer.parseInt(s) >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean laSoKhongAm(String s) {
        try {
            return Integer.parseInt(s) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /* ==================== QUYỀN ==================== */

    private void xinQuyenQuanTri() {
        if (QuanTriReceiver.daBat(this)) {
            Toast.makeText(this, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, QuanTriReceiver.thanhPhan(this));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.giai_thich_quan_tri));
        startActivity(i);
    }

    private void xinQuyenLopPhu() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void xinBoToiUuPin() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void xinQuyenThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /* ==================== KHÁC ==================== */

    private void khoaThu() {
        if (!ch.daDatMa()) {
            Toast.makeText(this, R.string.chua_dat_ma, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Settings.canDrawOverlays(this) || !QuanTriReceiver.daBat(this)) {
            Toast.makeText(this, R.string.thieu_quyen, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, DichVuKhoa.class);
        i.setAction(DichVuKhoa.HANH_DONG_KHOA);
        startForegroundService(i);
    }

    private void xemNhatKy() {
        // Mới nhất lên đầu: cái vừa xảy ra mới là cái cần xem.
        String noi = NhatKy.docMoiNhatTruoc(this, 120);
        new AlertDialog.Builder(this)
                .setTitle(R.string.nhat_ky)
                .setMessage(noi.isEmpty() ? getString(R.string.nhat_ky_trong) : noi)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void xemThongKe() {
        NgatQuang nq = new NgatQuang(ch);
        long bayGio = System.currentTimeMillis();
        String noi = getString(R.string.thong_ke_noi,
                CauHinh.doDai(ch.nqTongHomNay()),
                ch.nqSoDotHomNay(),
                ch.nqSoKhanCapHomNay(),
                CauHinh.doDai(nq.daDung(bayGio)),
                ch.nqPhutDung());
        new AlertDialog.Builder(this)
                .setTitle(R.string.thong_ke)
                .setMessage(noi)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void khoiDongDichVu() {
        Intent i = new Intent(this, DichVuKhoa.class);
        i.setAction(DichVuKhoa.HANH_DONG_CANH_GIU);
        startForegroundService(i);
    }

    private void capNhatTinhTrang() {
        StringBuilder sb = new StringBuilder();
        sb.append(danhDau(QuanTriReceiver.daBat(this))).append(' ')
                .append(getString(R.string.tt_quan_tri)).append('\n');
        sb.append(danhDau(Settings.canDrawOverlays(this))).append(' ')
                .append(getString(R.string.tt_lop_phu)).append('\n');

        PowerManager pm = getSystemService(PowerManager.class);
        sb.append(danhDau(pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())))
                .append(' ').append(getString(R.string.tt_pin)).append('\n');

        sb.append(danhDau(ch.daDatMa())).append(' ')
                .append(getString(R.string.tt_ma)).append('\n');

        if (ch.daDatMa() && ch.mocKhoa() > 0) {
            sb.append('\n').append(getString(R.string.khoa_luc, LenLich.gioPhut(ch.mocKhoa())));
        }
        if (ch.ngatQuangBat()) {
            sb.append('\n').append(getString(R.string.ngat_quang_dang_bat,
                    ch.nqPhutDung(), ch.nqPhutNghi()));
        }
        tinhTrang.setText(sb.toString());
    }

    private String danhDau(boolean xong) {
        return xong ? "✔" : "✘";
    }
}
