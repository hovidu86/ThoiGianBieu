package com.thoigianbieu.kiemsoat;

/**
 * Luật dùng ngắt quãng.
 *
 * Chỉ tính thời gian màn hình đang bật VÀ máy đã mở khoá — tắt màn hình nghe
 * nhạc thì không tính. Thời gian cộng dồn qua nhiều lần mở màn hình rời rạc;
 * dùng đủ hạn mức thì bắt buộc nghỉ. Nhưng nếu khoảng nghỉ giữa hai lần mở đủ
 * dài thì bộ đếm về 0 — đó chính là mục đích: nghỉ tử tế thì được dùng tiếp.
 *
 * Khoảng nghỉ CHƯA đủ ngưỡng reset thì tuỳ chế độ người dùng chọn:
 * <ul>
 *   <li>{@link #CHE_DO_CU}: không tính gì, chỉ reset khi nghỉ đủ.</li>
 *   <li>{@link #CHE_DO_HOI_DAN}: mỗi phút tắt màn hình hồi lại
 *       (hạn mức ÷ ngưỡng reset) phút dùng — tắt đủ ngưỡng thì hồi đầy,
 *       trùng với reset.</li>
 *   <li>{@link #CHE_DO_TRU_NGHI}: bộ đếm không đổi, nhưng tới hạn thì quãng
 *       nghỉ bắt buộc được trừ đi quãng tắt màn hình dài nhất trong đợt.</li>
 * </ul>
 * Cả hai chế độ sau chỉ tính những lần tắt từ {@link #NGUONG_TINH_NGHI} trở
 * lên — tắt vài giây nhìn thông báo không phải là nghỉ. Lý do có hai chế độ
 * này: người dùng còn 10 giây, tắt máy 4 phút, mở lại dùng nốt 10 giây rồi
 * phải nghỉ tiếp 5 phút — tổng 9 phút chờ cho một quãng nghỉ 5 phút.
 *
 * Toàn bộ trạng thái nằm trong SharedPreferences chứ không nằm trong bộ nhớ,
 * để dịch vụ bị hệ thống giết rồi dựng lại vẫn đếm tiếp đúng chỗ cũ.
 */
public class NgatQuang {

    public static final int CHE_DO_CU = 0;
    public static final int CHE_DO_HOI_DAN = 1;
    public static final int CHE_DO_TRU_NGHI = 2;

    /** Tắt màn hình ngắn hơn chừng này thì không coi là nghỉ (ms). */
    public static final long NGUONG_TINH_NGHI = 60_000L;

    /**
     * Chế độ trừ vào quãng nghỉ: phần còn phải nghỉ ngắn hơn chừng này thì
     * không khoá nữa, coi như đã nghỉ đủ — dựng lớp phủ 20 giây chỉ gây khó
     * chịu chứ không nghỉ thêm được gì.
     */
    public static final long NGUONG_BO_NGHI = 30_000L;

    private final CauHinh ch;

    public NgatQuang(CauHinh ch) {
        this.ch = ch;
    }

    /** Tính năng có đang bật và có đang trong khung giờ áp dụng không. */
    public boolean dangApDung(long bayGio) {
        return ch.ngatQuangBat() && ch.nqTrongKhungGio(bayGio);
    }

    /* ---------- luật thuần, không đụng tới trạng thái: kiểm thử được ---------- */

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

    /**
     * Chế độ hồi dần: tắt màn hình {@code tatMs} thì được hồi lại bao nhiêu ms
     * dùng. Tỉ lệ = hạn mức ÷ ngưỡng reset, nên tắt đúng ngưỡng reset là hồi
     * đầy hạn mức. Dưới ngưỡng tính nghỉ thì không hồi gì.
     */
    public static long hoiPhucMs(long tatMs, int phutDung, int phutReset) {
        if (tatMs < NGUONG_TINH_NGHI || phutReset <= 0) return 0;
        return tatMs * phutDung / phutReset;
    }

    /**
     * Bộ đếm còn lại bao nhiêu sau một quãng tắt màn hình {@code tatMs} — phần
     * luật thuần dùng chung cho cả ba chế độ. Đủ ngưỡng reset thì về 0 bất kể
     * chế độ; chưa đủ thì chỉ chế độ hồi dần mới trừ.
     */
    public static long daDungSauTat(long daDungMs, long tatMs, int cheDo,
                                    int phutDung, int phutReset) {
        if (tatMs >= phutReset * 60_000L) return 0;
        if (cheDo == CHE_DO_HOI_DAN) {
            return Math.max(0, daDungMs - hoiPhucMs(tatMs, phutDung, phutReset));
        }
        return daDungMs;
    }

    /**
     * Chế độ trừ vào quãng nghỉ: phải nghỉ bao nhiêu ms khi tới hạn, biết quãng
     * tắt màn hình dài nhất trong đợt. Chế độ khác thì nghỉ trọn.
     */
    public static long doDaiNghiMs(int cheDo, int phutNghi, long nghiDaiNhatMs) {
        long nghi = phutNghi * 60_000L;
        if (cheDo == CHE_DO_TRU_NGHI) nghi -= nghiDaiNhatMs;
        return Math.max(0, nghi);
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
            ch.nqDatTrangThai(sauQuangTat(bayGio - batDauCu), bayGio, batDauCu);
            return;
        }
        // Không có phiên nào đang mở: xét theo lần tắt màn hình thật gần nhất.
        long lucTat = ch.nqLucTatManHinh();
        long daDung = lucTat > 0 ? sauQuangTat(bayGio - lucTat) : ch.nqDaDungMs();
        ch.nqDatTrangThai(daDung, bayGio, lucTat);
    }

    /**
     * Vừa qua một quãng tắt màn hình dài {@code tatMs}: trả về bộ đếm mới theo
     * chế độ đang chọn, đồng thời ghi nhớ quãng dài nhất cho chế độ trừ vào
     * quãng nghỉ. Ghi ở mọi chế độ, để đổi chế độ giữa đợt vẫn có số mà dùng.
     */
    private long sauQuangTat(long tatMs) {
        long daDung = daDungSauTat(ch.nqDaDungMs(), tatMs, ch.nqCheDo(),
                ch.nqPhutDung(), ch.nqPhutReset());
        if (daDung <= 0) {
            // Về 0 (do reset hoặc hồi đầy) là bắt đầu đợt mới: quãng nghỉ cũ
            // không còn thuộc đợt nào để mà trừ.
            ch.nqDatNghiDaiNhat(0);
        } else if (tatMs >= NGUONG_TINH_NGHI && tatMs > ch.nqNghiDaiNhat()) {
            ch.nqDatNghiDaiNhat(tatMs);
        }
        return daDung;
    }

    /** Tới hạn thì phải nghỉ bao lâu (ms), đã trừ theo chế độ đang chọn. */
    public long doDaiNghi() {
        return doDaiNghiMs(ch.nqCheDo(), ch.nqPhutNghi(), ch.nqNghiDaiNhat());
    }

    /**
     * Chế độ trừ vào quãng nghỉ: đã tắt màn hình gần đủ rồi, phần còn phải
     * nghỉ quá ngắn để đáng khoá. Người gọi nên {@link #boQuaNghi} thay cho
     * {@link #batDauNghi}.
     */
    public boolean nghiKhongDangKhoa() {
        return ch.nqCheDo() == CHE_DO_TRU_NGHI && doDaiNghi() < NGUONG_BO_NGHI;
    }

    /** Coi như đã nghỉ đủ: chốt thống kê, bộ đếm về 0, KHÔNG khoá. */
    public void boQuaNghi(long bayGio) {
        long batDau = ch.nqBatDauPhien();
        if (batDau > 0) ch.nqCongThemHomNay(Math.max(0, bayGio - batDau));
        ch.nqDatNghiDaiNhat(0);
        ch.nqDatNghiDaTru(0);
        // Màn hình vẫn đang bật, phiên mới mở ngay từ bây giờ.
        ch.nqDatTrangThai(0, bayGio, ch.nqLucTatManHinh());
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
            // Màn hình bật rồi tắt mà không có phiên dùng nào: người dùng chỉ
            // bấm nút nguồn nhìn giờ trên màn hình khoá, chưa mở khoá. Đó vẫn
            // là đang nghỉ — giữ mốc tắt cũ, kẻo tắt máy 4 phút mà nhìn giờ
            // một cái là quãng nghỉ bị tính lại từ đầu.
            long lucTat = ch.nqLucTatManHinh();
            ch.nqDatTrangThai(ch.nqDaDungMs(), 0, lucTat > 0 ? lucTat : bayGio);
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

        long nghi = doDaiNghi();
        ch.nqDatNghiDaTru(ch.nqPhutNghi() * 60_000L - nghi);
        ch.nqDatKetThucNghi(bayGio + nghi);
        ch.nqDatTrangThai(0, 0, bayGio);
        ch.nqDatNghiDaiNhat(0);
        ch.nqTangSoDot();
    }

    /** Hết giờ nghỉ, hoặc người dùng thoát khẩn cấp. */
    public void ketThucNghi(long bayGio) {
        ch.nqDatKetThucNghi(0);
        ch.nqDatNghiDaTru(0);
        ch.nqDatNghiDaiNhat(0);
        ch.nqDatTrangThai(0, bayGio, bayGio);
    }

    public boolean conLuotKhanCap() {
        return ch.nqKhanCapMoiNgay() > 0 && ch.nqSoKhanCapHomNay() < ch.nqKhanCapMoiNgay();
    }

    public int luotKhanCapConLai() {
        return Math.max(0, ch.nqKhanCapMoiNgay() - ch.nqSoKhanCapHomNay());
    }
}
