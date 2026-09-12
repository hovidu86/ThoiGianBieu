package com.thoigianbieu.kiemsoat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Sổ ghi các phiên DÙNG MÁY THẬT: từ lúc mở xong khoá màn hình hệ thống (và
 * không bị lớp khoá đêm của app che) cho tới lúc tắt màn hình. Màn hình sáng
 * mà còn khoá — nhìn giờ, xem thông báo — không phải là dùng máy, không ghi.
 *
 * Dùng cho lời nhắc buổi sáng: quãng không dùng máy dài nhất trong đêm chính
 * là lúc đi ngủ (đầu quãng) và lúc thức dậy (cuối quãng). Chọn quãng dài nhất
 * thay vì "lần tắt màn hình cuối" vì tới giờ nhắc người dùng thường đã cầm
 * máy buổi sáng rồi — lấy lần cuối sẽ ra 7 giờ sáng chứ không phải 22 giờ đêm.
 *
 * Toàn bộ nằm trong SharedPreferences riêng để dịch vụ bị giết rồi dựng lại
 * vẫn biết đang có phiên mở dở hay không. Chỉ giữ hai ngày gần nhất.
 */
public class PhienDung {

    private static final String TEP = "phien_dung";
    private static final long GIU_MS = 48L * 3600_000L;

    /** Quãng không dùng ngắn hơn chừng này thì không thể là một đêm ngủ. */
    public static final long NGU_TOI_THIEU_MS = 2L * 3600_000L;

    private final Context ctx;
    private final SharedPreferences p;

    public PhienDung(Context ctx) {
        this.ctx = ctx;
        p = ctx.getSharedPreferences(TEP, Context.MODE_PRIVATE);
    }

    /** Mốc bắt đầu phiên đang mở dở, 0 nếu không có. */
    public long dangMoTu() {
        return p.getLong("dangMoTu", 0L);
    }

    /** Người dùng vừa mở được máy. Đang có phiên mở rồi thì thôi. */
    public void batDau(long luc) {
        if (dangMoTu() > 0) return;
        p.edit().putLong("dangMoTu", luc).putLong("lucCuoi", luc).apply();
        NhatKy.ghi(ctx, "phien-dung", "bắt đầu " + LuatGiacNgu.gioHienTai(luc));
    }

    /**
     * Nhịp tim trong lúc phiên đang mở: ghi "vẫn còn dùng lúc này". Dịch vụ bị
     * hệ thống giết giữa phiên rồi dựng lại lúc 3 giờ sáng thì nhờ mốc này mà
     * chốt phiên ở chỗ gần đúng, không phải ở 3 giờ sáng — nếu không lời nhắc
     * buổi sáng sẽ gợi ý "lên giường lúc 03:00" kèm nút ghi một chạm, rất tai
     * hại.
     */
    public void nhipTim(long luc) {
        if (dangMoTu() <= 0) return;
        p.edit().putLong("lucCuoi", luc).apply();
    }

    /**
     * Phiên đang mở dở nhưng nhịp tim cuối đã quá cũ — nghĩa là dịch vụ đã
     * chết một quãng (nhịp tim bình thường một phút một lần). Phiên đó không
     * còn tin được là "vẫn đang dùng", phải chốt ở nhịp tim cuối rồi mở lại
     * từ đầu nếu người dùng thật sự đang cầm máy.
     */
    public boolean nhipTimDaCu(long bayGio) {
        long tu = dangMoTu();
        if (tu <= 0) return false;
        return bayGio - Math.max(tu, p.getLong("lucCuoi", tu)) > 3 * 60_000L;
    }

    /**
     * Dịch vụ vừa dựng lại mà thấy phiên còn mở dở: chốt ở nhịp tim cuối cùng
     * đã ghi, không phải ở bây giờ.
     */
    public void ketThucTheoNhipTim() {
        long tu = dangMoTu();
        if (tu <= 0) return;
        long cuoi = Math.max(tu, p.getLong("lucCuoi", tu));
        NhatKy.ghi(ctx, "phien-dung", "dịch vụ dựng lại, chốt phiên mở dở ở nhịp tim cuối "
                + LuatGiacNgu.gioHienTai(cuoi));
        ketThuc(cuoi);
    }

    /** Màn hình tắt hoặc bị khoá đêm che: chốt phiên đang mở vào sổ. */
    public void ketThuc(long luc) {
        long tu = dangMoTu();
        if (tu <= 0) return;
        List<long[]> ds = danhSach();
        if (luc > tu) ds.add(new long[]{tu, luc});
        luu(ds, luc);
        p.edit().putLong("dangMoTu", 0L).apply();
        NhatKy.ghi(ctx, "phien-dung", "kết thúc " + LuatGiacNgu.gioHienTai(luc)
                + ", dài " + CauHinh.doDaiGiay(luc - tu));
    }

    /** Các phiên đã chốt, theo thứ tự ghi. Mỗi phần tử là {bắt đầu, kết thúc}. */
    public List<long[]> danhSach() {
        List<long[]> ra = new ArrayList<>();
        try {
            JSONArray m = new JSONArray(p.getString("phien", "[]"));
            for (int i = 0; i < m.length(); i++) {
                JSONArray c = m.getJSONArray(i);
                ra.add(new long[]{c.getLong(0), c.getLong(1)});
            }
        } catch (Exception ignore) { }
        return ra;
    }

    private void luu(List<long[]> ds, long bayGio) {
        JSONArray m = new JSONArray();
        for (long[] c : ds) {
            if (bayGio - c[1] > GIU_MS) continue;   // cũ quá, bỏ
            JSONArray a = new JSONArray();
            a.put(c[0]);
            a.put(c[1]);
            m.put(a);
        }
        p.edit().putString("phien", m.toString()).apply();
    }

    /* ---------- luật thuần, kiểm thử được bằng Java ---------- */

    /**
     * Quãng không dùng máy dài nhất có điểm ĐẦU nằm trong [{@code tu}, {@code den}).
     * Quãng cuối cùng (sau phiên chốt sau chót) kéo tới {@code moc} — là mốc
     * bắt đầu phiên đang mở dở, hoặc "bây giờ" nếu không có phiên nào mở.
     * Ngắn hơn {@link #NGU_TOI_THIEU_MS} thì không tính. Trả về
     * {bắt đầu, kết thúc} hoặc null nếu không có quãng nào đáng kể.
     */
    public static long[] quangNghiDaiNhat(List<long[]> phien, long tu, long den, long moc) {
        List<long[]> ds = new ArrayList<>(phien);
        Collections.sort(ds, new Comparator<long[]>() {
            @Override public int compare(long[] a, long[] b) { return Long.compare(a[0], b[0]); }
        });
        // Gộp các phiên chồng lấn/kề nhau — sổ ghi có thể lệch vài mili giây
        // khi dịch vụ dựng lại giữa chừng.
        List<long[]> gon = new ArrayList<>();
        for (long[] c : ds) {
            if (!gon.isEmpty() && c[0] <= gon.get(gon.size() - 1)[1]) {
                long[] cuoi = gon.get(gon.size() - 1);
                cuoi[1] = Math.max(cuoi[1], c[1]);
            } else {
                gon.add(new long[]{c[0], c[1]});
            }
        }

        long[] tot = null;
        for (int i = 0; i < gon.size(); i++) {
            long batDau = gon.get(i)[1];
            long ketThuc = i + 1 < gon.size() ? gon.get(i + 1)[0] : moc;
            if (batDau < tu || batDau >= den) continue;
            long dai = ketThuc - batDau;
            if (dai < NGU_TOI_THIEU_MS) continue;
            if (tot == null || dai > tot[1] - tot[0]) tot = new long[]{batDau, ketThuc};
        }
        return tot;
    }

    /** 18:00 của ngày {@code yyyy-MM-dd} — mốc bắt đầu một "đêm" theo luật giấc ngủ. */
    public static long dauDem(String ngay) {
        String[] p = ngay.split("-");
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]),
                LuatGiacNgu.MOC_CHIA_NGAY, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * "HH:mm" làm tròn tới 5 phút gần nhất — bảng chọn giờ của app chỉ có các
     * mức 5 phút, đưa 22:47 vào là không chọn lại được.
     */
    public static String gioTron5(long luc) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(luc);
        int phut = c.get(Calendar.MINUTE);
        int tron = (int) Math.round(phut / 5.0) * 5;
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, tron);   // 58 → 60 thì tự nhảy sang giờ sau
        return LuatGiacNgu.gioHienTai(c.getTimeInMillis());
    }
}
