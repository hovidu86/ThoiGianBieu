package com.thoigianbieu.kiemsoat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nơi cất giữ mọi thứ của module Giấc ngủ: cấu hình, danh sách đêm, và
 * checklist của đêm nay. Ghi ra tệp JSON trong bộ nhớ riêng của app.
 *
 * Giữ nguyên mô hình dữ liệu của bản PWA để cùng đọc được một Google Sheet.
 */
public class KhoGiacNgu {

    private static final String TEP_CAI_DAT = "cai-dat-ngu.json";
    private static final String TEP_DEM = "dem-ngu.json";
    private static final String TEP_DEM_NAY = "dem-nay.json";

    private static KhoGiacNgu chung;

    public static synchronized KhoGiacNgu cua(Context ctx) {
        if (chung == null) {
            chung = new KhoGiacNgu(ctx.getApplicationContext());
            chung.doc();
        }
        return chung;
    }

    private final Context ctx;

    public CaiDatNgu caiDat = CaiDatNgu.macDinh();
    public List<DemNgu> danhSach = new ArrayList<>();
    public int chuoiHienTai;
    public int chuoiDaiNhat;

    /** Checklist chuẩn bị ngủ của đêm nay. */
    public String demNay = "";
    public Set<Integer> daTich = new HashSet<>();

    private KhoGiacNgu(Context ctx) {
        this.ctx = ctx;
    }

    /* ==================== ĐỌC / GHI ==================== */

    public void doc() {
        String s = docTep(TEP_CAI_DAT);
        if (!s.isEmpty()) {
            try { caiDat = doiCaiDat(new JSONObject(s)); } catch (Exception ignore) { }
        }

        danhSach = new ArrayList<>();
        String d = docTep(TEP_DEM);
        if (!d.isEmpty()) {
            try {
                JSONArray m = new JSONArray(d);
                for (int i = 0; i < m.length(); i++) danhSach.add(doiDem(m.getJSONObject(i)));
            } catch (Exception ignore) { }
        }

        String n = docTep(TEP_DEM_NAY);
        daTich = new HashSet<>();
        demNay = "";
        if (!n.isEmpty()) {
            try {
                JSONObject o = new JSONObject(n);
                demNay = o.optString("ngay", "");
                JSONArray t = o.optJSONArray("tich");
                if (t != null) for (int i = 0; i < t.length(); i++) daTich.add(t.getInt(i));
            } catch (Exception ignore) { }
        }

        String demBayGio = LuatGiacNgu.demHienTai();
        if (!demBayGio.equals(demNay)) {
            demNay = demBayGio;
            daTich = new HashSet<>();
            luuDemNay();
        }

        tinhLai();
    }

    public void luuCaiDat() {
        ghiTep(TEP_CAI_DAT, raJson(caiDat).toString());
    }

    public void luuDanhSach() {
        JSONArray m = new JSONArray();
        for (DemNgu d : danhSach) m.put(raJson(d));
        ghiTep(TEP_DEM, m.toString());
    }

    public void luuDemNay() {
        try {
            JSONObject o = new JSONObject();
            o.put("ngay", demNay);
            o.put("tich", new JSONArray(new ArrayList<>(daTich)));
            ghiTep(TEP_DEM_NAY, o.toString());
        } catch (Exception ignore) { }
    }

    /**
     * Bỏ những dấu tích trỏ vào bước đã bị xoá. Gọi sau mỗi lần sửa danh sách
     * bước, nếu không checklist sẽ hiện kiểu "(5/3)" và thưởng thói quen được
     * trao dù người dùng chưa làm đủ.
     */
    public void cheoLaiDauTich() {
        int soBuoc = caiDat.cacBuoc.size();
        Set<Integer> con = new HashSet<>();
        for (Integer i : daTich) if (i != null && i >= 0 && i < soBuoc) con.add(i);
        if (con.size() != daTich.size()) {
            daTich = con;
            luuDemNay();
        }
    }

    /** Sang đêm mới thì xoá sạch checklist. Trả về true nếu vừa đổi đêm. */
    public boolean doiDemNeuCan() {
        String demBayGio = LuatGiacNgu.demHienTai();
        if (demBayGio.equals(demNay)) return false;
        demNay = demBayGio;
        daTich = new HashSet<>();
        luuDemNay();
        return true;
    }

    /* ==================== TÍNH LẠI ==================== */

    /**
     * Tính lại toàn bộ. Đêm nào đổi kết quả thì đánh dấu phải gửi lại lên
     * Sheets — sửa một mốc tiền là mọi đêm cũ đổi theo, không đánh dấu thì
     * Sheets giữ mãi số cũ.
     */
    public void tinhLai() {
        Map<String, String> truoc = new HashMap<>();
        for (DemNgu d : danhSach) truoc.put(d.ngay, d.dauVan());

        LuatGiacNgu.BangTinh bt = LuatGiacNgu.tinhLai(danhSach, caiDat);
        for (DemNgu d : bt.danhSach) {
            String cu = truoc.get(d.ngay);
            if (cu != null && !cu.equals(d.dauVan())) d.daGui = false;
        }
        danhSach = bt.danhSach;
        chuoiDaiNhat = bt.chuoiDaiNhat;
        chuoiHienTai = bt.chuoiHienTai;
    }

    public long soDu() {
        long t = 0;
        for (DemNgu d : danhSach) t += d.tongTien;
        return t;
    }

    public List<DemNgu> chuaGui() {
        List<DemNgu> ra = new ArrayList<>();
        for (DemNgu d : danhSach) if (!d.daGui) ra.add(d);
        return ra;
    }

    public DemNgu timTheoNgay(String ngay) {
        for (DemNgu d : danhSach) if (d.ngay.equals(ngay)) return d;
        return null;
    }

    /** Ghi một đêm, ghi đè nếu đã có. Trả về bản ghi sau khi tính lại. */
    public DemNgu ghiDem(String ngay, String gio, String gioDay, int soBuocXong) {
        List<DemNgu> con = new ArrayList<>();
        for (DemNgu d : danhSach) if (!d.ngay.equals(ngay)) con.add(d);
        con.add(new DemNgu(ngay, gio, gioDay, soBuocXong));
        danhSach = con;
        tinhLai();
        luuDanhSach();
        return timTheoNgay(ngay);
    }

    public void xoaDem(String ngay) {
        List<DemNgu> con = new ArrayList<>();
        for (DemNgu d : danhSach) if (!d.ngay.equals(ngay)) con.add(d);
        danhSach = con;
        tinhLai();
        luuDanhSach();
    }

    /** Trộn dữ liệu kéo về từ Sheets. Bản trên máy thắng nếu đang chờ gửi. */
    public int tronTuSheet(List<DemNgu> tuSheet) {
        Map<String, DemNgu> theoNgay = new HashMap<>();
        for (DemNgu d : danhSach) theoNgay.put(d.ngay, d);

        int them = 0;
        for (DemNgu s : tuSheet) {
            if (s.ngay == null || s.ngay.isEmpty() || s.gio == null || s.gio.isEmpty()) continue;
            DemNgu co = theoNgay.get(s.ngay);
            if (co != null && !co.daGui) continue;   // bản trên máy chưa gửi, giữ nguyên
            s.daGui = true;
            s.daXacNhan = true;
            theoNgay.put(s.ngay, s);
            if (co == null) them++;
        }
        danhSach = new ArrayList<>(theoNgay.values());
        tinhLai();
        luuDanhSach();
        return them;
    }

    /* ==================== JSON ==================== */

    private CaiDatNgu doiCaiDat(JSONObject o) {
        CaiDatNgu c = CaiDatNgu.macDinh();
        c.urlWebApp = o.optString("urlWebApp", "");
        c.phatMoiGioVuot = o.optLong("phatMoiGioVuot", c.phatMoiGioVuot);
        c.truocGioNay = o.optString("truocGioNay", c.truocGioNay);
        c.matChuoiSau = o.optString("matChuoiSau", c.matChuoiSau);
        c.nhacLuc = o.optString("nhacLuc", c.nhacLuc);
        c.nhacSangLuc = o.optString("nhacSangLuc", c.nhacSangLuc);
        c.phutChuanBi = o.optInt("phutChuanBi", c.phutChuanBi);
        c.thuongThoiQuen = o.optLong("thuongThoiQuen", c.thuongThoiQuen);

        JSONArray m = o.optJSONArray("cacMuc");
        if (m != null && m.length() > 0) {
            c.cacMuc = new ArrayList<>();
            for (int i = 0; i < m.length(); i++) {
                JSONObject x = m.optJSONObject(i);
                if (x == null) continue;
                c.cacMuc.add(new CaiDatNgu.Muc(x.optString("den"), x.optLong("tien"), x.optString("loi")));
            }
        }
        JSONArray k = o.optJSONArray("cacMocChuoi");
        if (k != null && k.length() > 0) {
            c.cacMocChuoi = new ArrayList<>();
            for (int i = 0; i < k.length(); i++) {
                JSONObject x = k.optJSONObject(i);
                if (x == null) continue;
                c.cacMocChuoi.add(new CaiDatNgu.MocChuoi(x.optInt("ngay"), x.optLong("tien")));
            }
        }
        JSONArray b = o.optJSONArray("cacBuoc");
        if (b != null && b.length() > 0) {
            c.cacBuoc = new ArrayList<>();
            for (int i = 0; i < b.length(); i++) {
                JSONObject x = b.optJSONObject(i);
                if (x == null) continue;
                c.cacBuoc.add(new CaiDatNgu.Buoc(x.optString("nhan"), x.optInt("phut")));
            }
        }
        return c;
    }

    private JSONObject raJson(CaiDatNgu c) {
        try {
            JSONObject o = new JSONObject();
            o.put("urlWebApp", c.urlWebApp);
            o.put("phatMoiGioVuot", c.phatMoiGioVuot);
            o.put("truocGioNay", c.truocGioNay);
            o.put("matChuoiSau", c.matChuoiSau);
            o.put("nhacLuc", c.nhacLuc);
            o.put("nhacSangLuc", c.nhacSangLuc);
            o.put("phutChuanBi", c.phutChuanBi);
            o.put("thuongThoiQuen", c.thuongThoiQuen);

            JSONArray m = new JSONArray();
            for (CaiDatNgu.Muc x : c.cacMuc) {
                JSONObject j = new JSONObject();
                j.put("den", x.den); j.put("tien", x.tien); j.put("loi", x.loi);
                m.put(j);
            }
            o.put("cacMuc", m);

            JSONArray k = new JSONArray();
            for (CaiDatNgu.MocChuoi x : c.cacMocChuoi) {
                JSONObject j = new JSONObject();
                j.put("ngay", x.ngay); j.put("tien", x.tien);
                k.put(j);
            }
            o.put("cacMocChuoi", k);

            JSONArray b = new JSONArray();
            for (CaiDatNgu.Buoc x : c.cacBuoc) {
                JSONObject j = new JSONObject();
                j.put("nhan", x.nhan); j.put("phut", x.phut);
                b.put(j);
            }
            o.put("cacBuoc", b);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private DemNgu doiDem(JSONObject o) {
        DemNgu d = new DemNgu();
        d.ngay = o.optString("ngay", "");
        d.gio = o.optString("gio", "");
        d.gioDay = o.optString("gioDay", "");
        d.soBuocXong = o.optInt("soBuocXong", 0);
        d.daGui = o.optBoolean("daGui", false);
        d.daXacNhan = o.optBoolean("daXacNhan", false);
        // Hai trường này là kết quả tính, nhưng vẫn phải lưu: dấu vân tay dùng
        // chúng để biết đêm nào vừa đổi kết quả. Không lưu thì mỗi lần mở app
        // dấu vân tay cũ luôn là "|0|0", mọi đêm bị coi là vừa đổi, và cả cuốn
        // lịch sử bị đẩy lại lên Sheet.
        d.tongTien = o.optLong("tongTien", 0);
        d.chuoiSauDem = o.optInt("chuoiSauDem", 0);
        return d;
    }

    private JSONObject raJson(DemNgu d) {
        try {
            JSONObject o = new JSONObject();
            o.put("ngay", d.ngay);
            o.put("gio", d.gio);
            o.put("gioDay", d.gioDay);
            o.put("soBuocXong", d.soBuocXong);
            o.put("daGui", d.daGui);
            o.put("daXacNhan", d.daXacNhan);
            o.put("tongTien", d.tongTien);
            o.put("chuoiSauDem", d.chuoiSauDem);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /* ==================== TỆP ==================== */

    private String docTep(String ten) {
        File f = new File(ctx.getFilesDir(), ten);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream ra = new ByteArrayOutputStream()) {
            byte[] dem = new byte[8192];
            int n;
            while ((n = in.read(dem)) > 0) ra.write(dem, 0, n);
            return ra.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void ghiTep(String ten, String noi) {
        try (FileOutputStream ra = new FileOutputStream(new File(ctx.getFilesDir(), ten))) {
            ra.write(noi.getBytes("UTF-8"));
        } catch (Exception e) {
            NhatKy.ghi(ctx, "loi-ghi-tep", ten + ": " + e.getMessage());
        }
    }
}
