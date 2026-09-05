package com.thoigianbieu.kiemsoat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Điều khiển tab Khoá máy.
 *
 * Trước đây đây là một Activity riêng, nên bấm vào là mất thanh tab dưới cùng
 * và không chuyển nhanh sang tab khác được. Nay nó chỉ là một lớp thường gắn
 * vào trang thứ tư của ViewFlipper trong ChinhActivity.
 */
public class ManKiemSoat {

    private final Activity ac;
    private final View goc;
    private CauHinh ch;
    private EditText oLapLai, oCanhBao, oKhoangCach, oMa, oMa2;
    private EditText oNqPhutDung, oNqPhutNghi, oNqPhutReset, oNqCanhBao, oNqKhanCap;
    // Bốn ô giờ là TextView: bấm vào mở bảng chọn giờ của hệ thống.
    private TextView oGioKhoa, oGioKetThuc, oNqTuGio, oNqDenGio;
    private CheckBox chkKhoaBat, chkNqBat, chkNqKhongKhoaKhiGoi;
    private TextView tinhTrang;

    public ManKiemSoat(Activity ac, View goc) {
        this.ac = ac;
        this.goc = goc;
        ch = new CauHinh(ac);

        chkKhoaBat = goc.findViewById(R.id.chk_khoa_bat);
        oGioKhoa = goc.findViewById(R.id.o_gio_khoa);
        oGioKetThuc = goc.findViewById(R.id.o_gio_ket_thuc);
        oLapLai = goc.findViewById(R.id.o_lap_lai);
        oCanhBao = goc.findViewById(R.id.o_canh_bao);
        oKhoangCach = goc.findViewById(R.id.o_khoang_cach);
        oMa = goc.findViewById(R.id.o_ma);
        oMa2 = goc.findViewById(R.id.o_ma_2);
        tinhTrang = goc.findViewById(R.id.tinh_trang);

        oNqPhutDung = goc.findViewById(R.id.o_nq_phut_dung);
        oNqPhutNghi = goc.findViewById(R.id.o_nq_phut_nghi);
        oNqPhutReset = goc.findViewById(R.id.o_nq_phut_reset);
        oNqCanhBao = goc.findViewById(R.id.o_nq_canh_bao);
        oNqTuGio = goc.findViewById(R.id.o_nq_tu_gio);
        oNqDenGio = goc.findViewById(R.id.o_nq_den_gio);
        oNqKhanCap = goc.findViewById(R.id.o_nq_khan_cap);
        chkNqBat = goc.findViewById(R.id.chk_nq_bat);
        chkNqKhongKhoaKhiGoi = goc.findViewById(R.id.chk_nq_khong_khoa_khi_goi);

        chkKhoaBat.setChecked(ch.khoaTheoGioBat());
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

        oGioKhoa.setOnClickListener(v ->
                GiaoDien.chonGio(ac, oGioKhoa.getText().toString(), oGioKhoa::setText));
        oGioKetThuc.setOnClickListener(v ->
                GiaoDien.chonGio(ac, oGioKetThuc.getText().toString(), oGioKetThuc::setText));
        oNqTuGio.setOnClickListener(v ->
                GiaoDien.chonGio(ac, oNqTuGio.getText().toString(), oNqTuGio::setText));
        oNqDenGio.setOnClickListener(v ->
                GiaoDien.chonGio(ac, oNqDenGio.getText().toString(), oNqDenGio::setText));

        goc.findViewById(R.id.nut_thong_tin).setOnClickListener(v -> hienTinhTrangDayDu());
        ((Button) goc.findViewById(R.id.nut_luu)).setOnClickListener(v -> luu());
        ((Button) goc.findViewById(R.id.nut_thong_ke)).setOnClickListener(v -> xemThongKe());
        ((Button) goc.findViewById(R.id.nut_quyen_quan_tri)).setOnClickListener(v -> xinQuyenQuanTri());
        ((Button) goc.findViewById(R.id.nut_quyen_lop_phu)).setOnClickListener(v -> xinQuyenLopPhu());
        ((Button) goc.findViewById(R.id.nut_quyen_pin)).setOnClickListener(v -> xinBoToiUuPin());
        ((Button) goc.findViewById(R.id.nut_khoa_thu)).setOnClickListener(v -> khoaThu());
        ((Button) goc.findViewById(R.id.nut_nhat_ky)).setOnClickListener(v -> xemNhatKy());

        xinQuyenThongBao();
    }

    /** ChinhActivity gọi lại mỗi lần quay lại app, để soát lại quyền. */
    public void capNhat() {
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

        if (!CauHinh.laGio(gioKhoa)) loi.add(ac.getString(R.string.loi_gio_khoa));
        if (!CauHinh.laGio(gioKet)) loi.add(ac.getString(R.string.loi_gio_ket_thuc));
        if (!laSoDuong(lapLai)) loi.add(ac.getString(R.string.loi_lap_lai));
        if (!laSoDuong(khoang)) loi.add(ac.getString(R.string.loi_khoang_cach));
        // Giới hạn trên: những mốc này cộng vào MA_CANH_BAO=2000 để làm mã báo
        // thức riêng (LenLich.java). Số quá lớn sẽ đụng mã của các báo thức
        // khác (nhịp, đếm lùi, hết hạn mức ngắt quãng) — 999 là dư sức cho một
        // lời cảnh báo trước khi khoá, không ai cần cảnh báo trước cả ngày trời.
        if (!canhBao.matches("^[0-9]+( *, *[0-9]+)*$") || coSoVuotNguong(canhBao, 999)) {
            loi.add(ac.getString(R.string.loi_canh_bao));
        }

        // --- dùng ngắt quãng ---
        String nqDung = oNqPhutDung.getText().toString().trim();
        String nqNghi = oNqPhutNghi.getText().toString().trim();
        String nqReset = oNqPhutReset.getText().toString().trim();
        String nqCanhBao = oNqCanhBao.getText().toString().trim();
        String nqTu = oNqTuGio.getText().toString().trim();
        String nqDen = oNqDenGio.getText().toString().trim();
        String nqKhanCap = oNqKhanCap.getText().toString().trim();

        if (!laSoDuong(nqDung)) loi.add(ac.getString(R.string.loi_nq_phut_dung));
        if (!laSoDuong(nqNghi)) loi.add(ac.getString(R.string.loi_nq_phut_nghi));
        if (!laSoDuong(nqReset)) loi.add(ac.getString(R.string.loi_nq_phut_reset));
        if (!CauHinh.laGio(nqTu) || !CauHinh.laGio(nqDen)) loi.add(ac.getString(R.string.loi_nq_gio));
        if (!laSoKhongAm(nqKhanCap)) loi.add(ac.getString(R.string.loi_nq_khan_cap));
        // Cảnh báo phải nằm trong đợt, nếu không thì cảnh báo nổ ngay lúc bắt đầu.
        if (!laSoKhongAm(nqCanhBao)
                || (laSoDuong(nqDung) && Integer.parseInt(nqCanhBao) >= Integer.parseInt(nqDung))) {
            loi.add(ac.getString(R.string.loi_nq_canh_bao));
        }

        boolean doiMa = !ma.isEmpty() || !ma2.isEmpty() || !ch.daDatMa();
        if (doiMa) {
            if (ma.length() < CauHinh.DO_DAI_MA_TOI_THIEU) {
                loi.add(ac.getString(R.string.loi_ma_ngan, CauHinh.DO_DAI_MA_TOI_THIEU));
            } else if (!ma.equals(ma2)) {
                loi.add(ac.getString(R.string.loi_ma_khong_khop));
            }
        }

        if (!loi.isEmpty()) {
            new AlertDialog.Builder(ac)
                    .setTitle(R.string.chua_luu_duoc)
                    .setMessage(String.join("\n\n", loi))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ch.luu(gioKhoa, gioKet, Integer.parseInt(lapLai), canhBao, Integer.parseInt(khoang),
                chkKhoaBat.isChecked());
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
        LenLich.datLai(ac);
        khoiDongDichVu();

        Toast.makeText(ac, ac.getString(R.string.da_luu, LenLich.gioPhut(ch.mocKhoa())),
                Toast.LENGTH_LONG).show();
        capNhatTinhTrang();
    }

    /** Có số nào trong danh sách "a, b, c" vượt quá nguong không. */
    private boolean coSoVuotNguong(String csv, int nguong) {
        for (String phan : csv.split(",")) {
            try {
                if (Integer.parseInt(phan.trim()) > nguong) return true;
            } catch (NumberFormatException ignore) {
                // Định dạng sai đã bị regex ở trên bắt riêng.
            }
        }
        return false;
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
        if (QuanTriReceiver.daBat(ac)) {
            Toast.makeText(ac, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, QuanTriReceiver.thanhPhan(ac));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ac.getString(R.string.giai_thich_quan_tri));
        ac.startActivity(i);
    }

    private void xinQuyenLopPhu() {
        if (Settings.canDrawOverlays(ac)) {
            Toast.makeText(ac, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        ac.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + ac.getPackageName())));
    }

    private void xinBoToiUuPin() {
        PowerManager pm = ac.getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(ac.getPackageName())) {
            Toast.makeText(ac, R.string.quyen_da_co, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ac.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ac.getPackageName())));
        } catch (Exception e) {
            ac.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void xinQuyenThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ac.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ac.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /* ==================== KHÁC ==================== */

    private void khoaThu() {
        if (!ch.daDatMa()) {
            Toast.makeText(ac, R.string.chua_dat_ma, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Settings.canDrawOverlays(ac) || !QuanTriReceiver.daBat(ac)) {
            Toast.makeText(ac, R.string.thieu_quyen, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(ac, DichVuKhoa.class);
        i.setAction(DichVuKhoa.HANH_DONG_KHOA);
        ac.startForegroundService(i);
    }

    /**
     * Bảng tình trạng đầy đủ, mở bằng nút i nhỏ ở góc thẻ Tình trạng.
     * Gom hết những gì đang diễn ra vào một chỗ, khỏi phải đoán.
     */
    private void hienTinhTrangDayDu() {
        long bayGio = System.currentTimeMillis();
        NgatQuang nq = new NgatQuang(ch);
        StringBuilder s = new StringBuilder();

        s.append("DỊCH VỤ NỀN\n");
        s.append(DichVuKhoa.dangChay ? "  Đang chạy\n" : "  KHÔNG chạy — mở lại app\n");

        s.append("\nKHOÁ THEO GIỜ\n");
        if (!ch.khoaTheoGioBat()) {
            s.append("  Đang tắt\n");
        } else {
            s.append("  Khoảng khoá: ").append(ch.gioKhoa()).append(" → ").append(ch.gioKetThuc()).append('\n');
            s.append("  Đang trong khoảng khoá: ")
                    .append(ch.trongKhoangKhoa(bayGio) ? "có" : "không").append('\n');
            if (ch.daDatMa() && ch.mocKhoa() > 0) {
                long con = ch.mocKhoa() - bayGio;
                s.append("  Lần khoá kế tiếp: ").append(LenLich.gioPhut(ch.mocKhoa()));
                if (con > 0) s.append("  (còn ").append(LuatGiacNgu.khoangCach(con)).append(")");
                s.append('\n');
            } else {
                s.append("  Chưa đặt mã mở khoá nên chưa khoá gì\n");
            }
            s.append("  Mở khoá xong khoá lại sau: ").append(ch.lapLaiPhut()).append(" phút\n");
            s.append("  Cảnh báo trước: ").append(ch.canhBaoPhut()).append(" phút\n");
        }

        s.append("\nDÙNG NGẮT QUÃNG\n");
        if (!ch.ngatQuangBat()) {
            s.append("  Đang tắt\n");
        } else {
            s.append("  Hạn mức: ").append(ch.nqPhutDung()).append(" phút, nghỉ ")
                    .append(ch.nqPhutNghi()).append(" phút\n");
            s.append("  Khung giờ: ").append(ch.nqTuGio().equals(ch.nqDenGio())
                    ? "cả ngày" : ch.nqTuGio() + " → " + ch.nqDenGio()).append('\n');
            s.append("  Đang trong khung giờ: ")
                    .append(ch.nqTrongKhungGio(bayGio) ? "có" : "không").append('\n');
            if (nq.dangNghi(bayGio)) {
                s.append("  ĐANG NGHỈ, còn ").append(nq.conNghi(bayGio) / 1000).append(" giây\n");
            } else {
                long daDung = nq.daDung(bayGio);
                s.append("  Đợt này đã dùng: ").append(daDung / 60_000).append(" phút ")
                        .append((daDung / 1000) % 60).append(" giây\n");
                s.append("  Còn lại: ").append(Math.max(0, nq.conLai(bayGio) / 60_000))
                        .append(" phút\n");
            }
            s.append("  Nghỉ ").append(ch.nqPhutReset()).append(" phút thì bộ đếm về 0\n");
        }

        s.append("\nHÔM NAY\n");
        s.append("  Tổng thời gian dùng: ").append(CauHinh.doDai(ch.nqTongHomNay())).append('\n');
        s.append("  Số đợt nghỉ bắt buộc: ").append(ch.nqSoDotHomNay()).append('\n');
        s.append("  Thoát khẩn cấp: đã dùng ").append(ch.nqSoKhanCapHomNay())
                .append('/').append(ch.nqKhanCapMoiNgay()).append('\n');
        s.append("  Số lần né tránh: ").append(ch.nqSoNeTranhHomNay()).append('\n');

        s.append("\nQUYỀN\n");
        PowerManager pm = ac.getSystemService(PowerManager.class);
        s.append("  ").append(danhDau(QuanTriReceiver.daBat(ac))).append(" quản trị thiết bị\n");
        s.append("  ").append(danhDau(Settings.canDrawOverlays(ac))).append(" lớp phủ\n");
        s.append("  ").append(danhDau(pm != null
                && pm.isIgnoringBatteryOptimizations(ac.getPackageName()))).append(" bỏ tối ưu pin\n");
        s.append("  ").append(danhDau(ch.daDatMa())).append(" đã đặt mã mở khoá");

        new AlertDialog.Builder(ac)
                .setTitle(R.string.tinh_trang_day_du)
                .setMessage(s.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void xemNhatKy() {
        // Mới nhất lên đầu: cái vừa xảy ra mới là cái cần xem.
        String noi = NhatKy.docMoiNhatTruoc(ac, 120);
        new AlertDialog.Builder(ac)
                .setTitle(R.string.nhat_ky)
                .setMessage(noi.isEmpty() ? ac.getString(R.string.nhat_ky_trong) : noi)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void xemThongKe() {
        NgatQuang nq = new NgatQuang(ch);
        long bayGio = System.currentTimeMillis();
        String noi = ac.getString(R.string.thong_ke_noi,
                CauHinh.doDai(ch.nqTongHomNay()),
                ch.nqSoDotHomNay(),
                ch.nqSoKhanCapHomNay(),
                CauHinh.doDai(nq.daDung(bayGio)),
                ch.nqPhutDung());
        new AlertDialog.Builder(ac)
                .setTitle(R.string.thong_ke)
                .setMessage(noi)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void khoiDongDichVu() {
        Intent i = new Intent(ac, DichVuKhoa.class);
        i.setAction(DichVuKhoa.HANH_DONG_CANH_GIU);
        ac.startForegroundService(i);
    }

    private void capNhatTinhTrang() {
        StringBuilder sb = new StringBuilder();
        sb.append(danhDau(QuanTriReceiver.daBat(ac))).append(' ')
                .append(ac.getString(R.string.tt_quan_tri)).append('\n');
        sb.append(danhDau(Settings.canDrawOverlays(ac))).append(' ')
                .append(ac.getString(R.string.tt_lop_phu)).append('\n');

        PowerManager pm = ac.getSystemService(PowerManager.class);
        sb.append(danhDau(pm != null && pm.isIgnoringBatteryOptimizations(ac.getPackageName())))
                .append(' ').append(ac.getString(R.string.tt_pin)).append('\n');

        sb.append(danhDau(ch.daDatMa())).append(' ')
                .append(ac.getString(R.string.tt_ma)).append('\n');

        if (!ch.khoaTheoGioBat()) {
            sb.append('\n').append(ac.getString(R.string.khoa_theo_gio_dang_tat));
        } else if (ch.daDatMa() && ch.mocKhoa() > 0) {
            sb.append('\n').append(ac.getString(R.string.khoa_luc, LenLich.gioPhut(ch.mocKhoa())));
        }
        if (ch.ngatQuangBat()) {
            sb.append('\n').append(ac.getString(R.string.ngat_quang_dang_bat,
                    ch.nqPhutDung(), ch.nqPhutNghi()));
        }
        tinhTrang.setText(sb.toString());
    }

    private String danhDau(boolean xong) {
        return xong ? "✔" : "✘";
    }
}
