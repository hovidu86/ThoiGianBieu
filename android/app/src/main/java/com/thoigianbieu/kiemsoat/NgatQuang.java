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

    /**
     * Dịch vụ bị giết rồi dựng lại trong khoảng ngắn hơn chừng này thì tin là
     * màn hình vẫn sáng suốt — cộng dồn quãng đó bình thường. Lâu hơn thì
     * không có gì đảm bảo màn hình không tắt/bật lại nhiều lần trong lúc dịch
     * vụ vắng mặt (ví dụ bị giết cả đêm) — tin mù số đó sẽ biến nguyên một đêm
     * ngủ thành "đã dùng nhiều tiếng", vừa mở khoá buổi sáng xong là hết hạn
     * mức ngay. Ngưỡng này chỉ cần đủ lớn hơn nhịp soát bình thường một chút.
     */
    private static final long NGUONG_TIN_PHIEN_MO = 2 * 60_000L;

    /** Màn hình vừa bật và máy đã mở khoá: bắt đầu tính giờ. */
    public void batDauDung(long bayGio) {
        long batDauCu = ch.nqBatDauPhien();
        if (batDauCu > 0 && bayGio - batDauCu <= NGUONG_TIN_PHIEN_MO) {
            // Phiên trước còn đang mở, khoảng cách ngắn: dịch vụ bị hệ thống
            // giết giữa lúc màn hình đang bật rồi dựng lại ngay. Chốt nốt
            // quãng đó rồi mở phiên mới. Không xét ngưỡng reset ở đây — màn
            // hình có tắt đâu mà gọi là nghỉ.
            long them = bayGio - batDauCu;
            ch.nqDatTrangThai(ch.nqDaDungMs() + them, bayGio, ch.nqLucTatManHinh());
            ch.nqCongThemHomNay(them);
            return;
        }
        if (batDauCu > 0) {
            // Phiên trước "còn mở" nhưng cách quá lâu để tin là liền mạch.
            // Vẫn là một phiên THẬT đã có — checkpoint() (gọi định kỳ trong
            // lúc đếm) giữ batDauCu luôn là mốc gần đây, nên xét reset dựa
            // trên chính mốc đó, KHÔNG dùng nqLucTatManHinh: mốc đó có thể cũ
            // từ trước khi phiên này mở, chẳng liên quan gì tới khoảng dịch vụ
            // vừa vắng mặt — dùng nhầm sẽ xoá oan cả một phiên dùng liên tục
            // thật sự chỉ vì dịch vụ chết mất vài phút giữa chừng.
            boolean reset = phaiReset(batDauCu, bayGio, ch.nqPhutReset());
            ch.nqDatTrangThai(reset ? 0 : ch.nqDaDungMs(), bayGio, batDauCu);
            return;
        }
        // Không có phiên nào đang mở: xét reset bình thường theo lần tắt màn
        // hình thật gần nhất đã biết.
        long lucTat = ch.nqLucTatManHinh();
        long daDung = phaiReset(lucTat, bayGio, ch.nqPhutReset()) ? 0 : ch.nqDaDungMs();
        ch.nqDatTrangThai(daDung, bayGio, lucTat);
    }

    /**
     * Chốt tạm phiên đang mở vào bộ đếm cộng dồn nhưng KHÔNG đóng phiên — chỉ
     * dời mốc bắt đầu tới bây giờ. Gọi định kỳ trong lúc đang đếm (mỗi lần
     * soát) để {@code nqBatDauPhien} luôn là một mốc gần đây thay vì mốc bắt
     * đầu phiên từ rất lâu — nhờ vậy nếu dịch vụ bị giết giữa chừng, lúc dựng
     * lại {@link #batDauDung} so sánh được với một mốc còn ý nghĩa.
     */
    public void checkpoint(long bayGio) {
        long batDau = ch.nqBatDauPhien();
        if (batDau <= 0) return;
        long them = bayGio - batDau;
        if (them <= 0) return;
        ch.nqDatTrangThai(ch.nqDaDungMs() + them, bayGio, ch.nqLucTatManHinh());
        ch.nqCongThemHomNay(them);
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
        // Chốt phiên đang mở vào thống kê ngày TRƯỚC khi xoá bộ đếm. Quên bước
        // này thì một mạch dùng 15 phút liền sẽ hiện "hôm nay đã dùng 0 phút"
        // ngay bên cạnh "số đợt nghỉ: 1".
        long batDau = ch.nqBatDauPhien();
        if (batDau > 0) ch.nqCongThemHomNay(Math.max(0, bayGio - batDau));

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
