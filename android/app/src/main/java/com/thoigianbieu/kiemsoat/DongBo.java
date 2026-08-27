package com.thoigianbieu.kiemsoat;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Nói chuyện với Google Apps Script Web App — cùng một Web App, cùng một
 * Google Sheet mà bản PWA đang dùng. Không đổi gì phía Apps Script.
 *
 * Mọi việc chạy trên luồng riêng, kết quả trả về luồng giao diện.
 */
public class DongBo {

    private static final int CHO_MS = 20_000;
    private static final Handler TAY = new Handler(Looper.getMainLooper());

    public interface Xong<T> {
        void thanhCong(T ketQua);
        void thatBai(String loi);
    }

    public static boolean urlHopLe(String url) {
        return url != null && url.trim().matches("^https://script\\.google\\.com/.+/exec$");
    }

    /* ==================== VIỆC ==================== */

    /** Gọi thử, trả về mô tả ngắn tình trạng Sheet. */
    public static void kiemTra(final String url, final Xong<String> xong) {
        chay(new Viec<String>() {
            @Override
            public String lam() throws Exception {
                JSONObject o = new JSONObject(tai(url + "?action=ping"));
                if (!"success".equals(o.optString("status"))) {
                    throw new Exception(o.optString("message", "Web App trả về lỗi"));
                }
                return "Kết nối tốt. Sheet \"" + o.optString("sheet") + "\" đang có "
                        + o.optInt("rows") + " dòng.";
            }
        }, xong);
    }

    /** Kéo toàn bộ dữ liệu đã lưu trên Sheet. */
    public static void keoVe(final String url, final Xong<List<DemNgu>> xong) {
        chay(new Viec<List<DemNgu>>() {
            @Override
            public List<DemNgu> lam() throws Exception {
                JSONObject o = new JSONObject(tai(url + "?action=list"));
                if (!"success".equals(o.optString("status"))) {
                    throw new Exception(o.optString("message", "Không đọc được dữ liệu"));
                }
                JSONArray m = o.optJSONArray("rows");
                List<DemNgu> ra = new ArrayList<>();
                if (m != null) {
                    for (int i = 0; i < m.length(); i++) {
                        JSONObject x = m.optJSONObject(i);
                        if (x == null) continue;
                        DemNgu d = new DemNgu();
                        d.ngay = x.optString("date", "");
                        d.gio = x.optString("time", "");
                        d.gioDay = x.optString("wake", "");
                        d.soBuocXong = x.optInt("routineDone", 0);
                        ra.add(d);
                    }
                }
                return ra;
            }
        }, xong);
    }

    /** Đẩy một hoặc nhiều đêm lên Sheet. Cùng ngày thì ghi đè, không nhân bản. */
    public static void day(final String url, final List<DemNgu> ds, final Xong<Integer> xong) {
        chay(new Viec<Integer>() {
            @Override
            public Integer lam() throws Exception {
                JSONObject goi = new JSONObject();
                goi.put("action", "save");
                JSONArray m = new JSONArray();
                for (DemNgu d : ds) m.put(raDong(d));
                goi.put("rows", m);

                JSONObject o = new JSONObject(gui(url, goi.toString()));
                if (!"success".equals(o.optString("status"))) {
                    throw new Exception(o.optString("message", "Ghi thất bại"));
                }
                return o.optInt("saved", ds.size());
            }
        }, xong);
    }

    /** Xoá một đêm khỏi Sheet. */
    public static void xoa(final String url, final String ngay, final Xong<Integer> xong) {
        chay(new Viec<Integer>() {
            @Override
            public Integer lam() throws Exception {
                JSONObject goi = new JSONObject();
                goi.put("action", "delete");
                goi.put("date", ngay);
                JSONObject o = new JSONObject(gui(url, goi.toString()));
                return o.optInt("deleted", 0);
            }
        }, xong);
    }

    /** Đúng tên trường mà Code.gs đang đọc, đừng đổi. */
    private static JSONObject raDong(DemNgu d) throws Exception {
        JSONObject j = new JSONObject();
        j.put("date", d.ngay);
        j.put("time", d.gio);
        j.put("wake", d.gioDay == null ? "" : d.gioDay);
        j.put("duration", d.thoiLuong == null ? "" : d.thoiLuong);
        j.put("baseAmount", d.tienGoc);
        j.put("streakBonus", d.thuongChuoi);
        j.put("routineBonus", d.thuongThoiQuen);
        j.put("routineDone", d.soBuocXong);
        j.put("totalAmount", d.tongTien);
        j.put("resultingStreak", d.chuoiSauDem);
        j.put("msg", d.loi == null ? "" : d.loi);
        j.put("streakMsg", d.loiChuoi == null ? "" : d.loiChuoi);
        return j;
    }

    /* ==================== HTTP ==================== */

    private static String tai(String diaChi) throws Exception {
        return goi(diaChi, null, 0);
    }

    private static String gui(String diaChi, String than) throws Exception {
        return goi(diaChi, than, 0);
    }

    /**
     * Gọi một lần, tự đi theo chuyển hướng.
     *
     * Apps Script luôn trả 302 sang googleusercontent.com. Với POST thì phải tự
     * đi theo bằng GET — mã script đã chạy xong ở bước POST rồi, trang được
     * chuyển hướng tới chỉ là nơi chứa kết quả. Để HttpURLConnection tự đi theo
     * thì nó cũng làm vậy nhưng lặng lẽ, khó gỡ lỗi khi hỏng.
     */
    private static String goi(String diaChi, String than, int sauLanChuyen) throws Exception {
        if (sauLanChuyen > 5) throw new Exception("Chuyển hướng vòng quanh quá nhiều lần");

        HttpURLConnection kn = (HttpURLConnection) new URL(diaChi).openConnection();
        try {
            kn.setConnectTimeout(CHO_MS);
            kn.setReadTimeout(CHO_MS);
            kn.setInstanceFollowRedirects(false);

            if (than != null) {
                kn.setRequestMethod("POST");
                kn.setDoOutput(true);
                // text/plain: giữ đúng như bản PWA, và Code.gs đọc từ postData.contents.
                kn.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
                try (OutputStream ra = kn.getOutputStream()) {
                    ra.write(than.getBytes("UTF-8"));
                }
            } else {
                kn.setRequestMethod("GET");
            }

            int ma = kn.getResponseCode();
            if (ma == 301 || ma == 302 || ma == 303 || ma == 307) {
                String toi = kn.getHeaderField("Location");
                if (toi == null || toi.isEmpty()) throw new Exception("Chuyển hướng thiếu địa chỉ");
                return goi(toi, null, sauLanChuyen + 1);
            }
            if (ma == 401 || ma == 403) {
                throw new Exception("Web App từ chối (HTTP " + ma + "). Deploy lại với "
                        + "\"Who has access\" = Anyone.");
            }
            if (ma < 200 || ma >= 300) {
                throw new Exception("HTTP " + ma);
            }

            String noi = docHet(kn.getInputStream());
            if (noi.trim().startsWith("<")) {
                throw new Exception("Web App trả về trang HTML thay vì dữ liệu. "
                        + "Kiểm tra lại quyền truy cập và URL phải kết thúc bằng /exec.");
            }
            return noi;
        } finally {
            kn.disconnect();
        }
    }

    private static String docHet(InputStream in) throws Exception {
        try (ByteArrayOutputStream ra = new ByteArrayOutputStream()) {
            byte[] dem = new byte[8192];
            int n;
            while ((n = in.read(dem)) > 0) ra.write(dem, 0, n);
            return ra.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    /* ==================== LUỒNG ==================== */

    private interface Viec<T> {
        T lam() throws Exception;
    }

    private static <T> void chay(final Viec<T> viec, final Xong<T> xong) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final T kq = viec.lam();
                    TAY.post(new Runnable() {
                        @Override public void run() { xong.thanhCong(kq); }
                    });
                } catch (final Exception e) {
                    final String loi = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                    TAY.post(new Runnable() {
                        @Override public void run() { xong.thatBai(loi); }
                    });
                }
            }
        }).start();
    }
}
