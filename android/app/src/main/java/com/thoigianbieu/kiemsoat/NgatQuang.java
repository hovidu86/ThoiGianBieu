package com.thoigianbieu.kiemsoat;

/**
 * Luật dùng ngắt quãng.
 *
 * Chỉ tính thời gian màn hình đang bật VÀ máy đã mở khoá — tắt màn hình nghe
 * nhạc thì không tính. Thời gian cộng dồn qua nhiều lần mở màn hình rời rạc;
 * dùng đủ hạn mức thì bắt buộc nghỉ. Nhưng nếu khoảng nghỉ giữa hai lần mở đủ
 * dài thì bộ đếm về 0 — đó chính là mục đích: nghỉ tử tế thì được dùng tiếp.
 *
 * Toàn bộ trạng thái nằm trong SharedPreferences chứ không nằm trong bộ nhớ,
 * để dịch vụ bị hệ thống giết rồi dựng lại vẫn đếm tiếp đúng chỗ cũ.
 */
public class NgatQuang {

    private final CauHinh ch;

    public NgatQuang(CauHinh ch) {
        this.ch = ch;
    }

    /** Tính năng có đang bật và có đang trong khung giờ áp dụng không. */
    public boolean dangApDung(long bayGio) {
        return ch.ngatQuangBat() && ch.nqTrongKhungGio(bayGio);
    }

    /* ---------- luật thuần, không đụng tới trạng thái: kiểm thử được ---------- */

    /**
     * Khoảng nghỉ vừa rồi có đủ dài để bộ đếm về 0 không.
     * {@code lucTat} bằng 0 nghĩa là chưa từng tắt màn hình.
     */
    public static boolean phaiReset(long lucTat, long bayGio, int phutReset) {
        return lucTat > 0 && bayGio - lucTat >= phutReset * 60_000L;
    }

    /** Tổng đã dùng trong đợt, gồm cả phiên đang mở dở ({@code batDauPhien} > 0). */
    public static long tongDaDung(long daDungMs, long batDauPhien, long bayGio) {
        long tong = daDungMs;
        if (batDauPhien > 0) tong += Math.max(0, bayGio - batDauPhien);
        return tong;
    }

    /** Còn được dùng bao lâu nữa. Số âm nghĩa là đã quá hạn mức. */
    public static long conLaiMs(long daDungMs, long batDauPhien, long bayGio, int phutDung) {
        return phutDung * 60_000L - tongDaDung(daDungMs, batDauPhien, bayGio);
    }

    /* ---------- vận hành trên trạng thái đã lưu ---------- */

    /** Màn hình vừa bật và máy đã mở khoá: bắt đầu tính giờ. */
    public void batDauDung(long bayGio) {
        long lucTat = ch.nqLucTatManHinh();
        long daDung = phaiReset(lucTat, bayGio, ch.nqPhutReset()) ? 0 : ch.nqDaDungMs();
        ch.nqDatTrangThai(daDung, bayGio, lucTat);
    }

    /** Màn hình tắt: chốt quãng vừa dùng vào bộ đếm cộng dồn. */
    public void dungDem(long bayGio) {
        long batDau = ch.nqBatDauPhien();
        if (batDau <= 0) {
            ch.nqDatTrangThai(ch.nqDaDungMs(), 0, bayGio);
            return;
        }
        long them = Math.max(0, bayGio - batDau);
        ch.nqDatTrangThai(ch.nqDaDungMs() + them, 0, bayGio);
        ch.nqCongThemHomNay(them);
    }

    /** Đã dùng bao lâu trong đợt hiện tại, kể cả phiên đang mở dở. */
    public long daDung(long bayGio) {
        return tongDaDung(ch.nqDaDungMs(), ch.nqBatDauPhien(), bayGio);
    }

    /** Còn được dùng bao lâu nữa trong đợt này. Âm nghĩa là đã quá hạn. */
    public long conLai(long bayGio) {
        return conLaiMs(ch.nqDaDungMs(), ch.nqBatDauPhien(), bayGio, ch.nqPhutDung());
    }

    public boolean toiHan(long bayGio) {
        return conLai(bayGio) <= 0;
    }

    public boolean dangNghi(long bayGio) {
        return ch.nqKetThucNghi() > bayGio;
    }

    public long conNghi(long bayGio) {
        return Math.max(0, ch.nqKetThucNghi() - bayGio);
    }

    /** Vào quãng nghỉ bắt buộc, bộ đếm đợt về 0. */
    public void batDauNghi(long bayGio) {
        ch.nqDatKetThucNghi(bayGio + ch.nqPhutNghi() * 60_000L);
        ch.nqDatTrangThai(0, 0, bayGio);
        ch.nqTangSoDot();
    }

    /** Hết giờ nghỉ, hoặc người dùng thoát khẩn cấp. */
    public void ketThucNghi(long bayGio) {
        ch.nqDatKetThucNghi(0);
        ch.nqDatTrangThai(0, bayGio, bayGio);
    }

    public boolean conLuotKhanCap() {
        return ch.nqKhanCapMoiNgay() > 0 && ch.nqSoKhanCapHomNay() < ch.nqKhanCapMoiNgay();
    }

    public int luotKhanCapConLai() {
        return Math.max(0, ch.nqKhanCapMoiNgay() - ch.nqSoKhanCapHomNay());
    }
}
