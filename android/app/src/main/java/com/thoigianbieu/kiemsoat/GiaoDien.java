package com.thoigianbieu.kiemsoat;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

import java.util.Calendar;

/** Mấy việc giao diện dùng lại ở nhiều màn hình. */
public class GiaoDien {

    public interface Nhan {
        void nhan(String giaTri);
    }

    /**
     * Chừa chỗ cho thanh trạng thái và thanh điều hướng của hệ thống.
     *
     * Từ Android 15, app nhắm SDK 35 trở lên luôn vẽ tràn ra sau hai thanh đó,
     * không có cách tắt. Không tự chừa thì tên app nằm lọt dưới đồng hồ và biểu
     * tượng pin — đúng lỗi đã thấy trên máy thật.
     */
    public static void chuaChoThanhHeThong(final View goc, final View tren, final View duoi) {
        final int demTren = tren == null ? 0 : tren.getPaddingTop();
        final int demDuoi = duoi == null ? 0 : duoi.getPaddingBottom();

        goc.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                int cao, thap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Insets i = ins.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout()
                            // Tính cả bàn phím: cửa sổ vẽ tràn viền thì hệ thống
                            // không tự co nữa, không cộng vào đây là bàn phím che
                            // mất ô nhập ở cuối trang.
                            | WindowInsets.Type.ime());
                    cao = i.top;
                    thap = i.bottom;
                } else {
                    cao = ins.getSystemWindowInsetTop();
                    thap = ins.getSystemWindowInsetBottom();
                }
                if (tren != null) {
                    tren.setPadding(tren.getPaddingLeft(), demTren + cao,
                            tren.getPaddingRight(), tren.getPaddingBottom());
                }
                if (duoi != null) {
                    duoi.setPadding(duoi.getPaddingLeft(), duoi.getPaddingTop(),
                            duoi.getPaddingRight(), demDuoi + thap);
                    cuonToiODangGo(duoi);
                }
                return ins;
            }
        });
        goc.requestApplyInsets();
    }

    /** Kéo ô đang gõ lên trên bàn phím, nếu vùng cuộn có ô nào đang gõ. */
    private static void cuonToiODangGo(View vung) {
        if (!(vung instanceof android.widget.ScrollView)) return;
        final android.widget.ScrollView cuon = (android.widget.ScrollView) vung;
        final View dangGo = cuon.findFocus();
        if (dangGo == null) return;
        cuon.post(new Runnable() {
            @Override
            public void run() {
                android.graphics.Rect r = new android.graphics.Rect();
                dangGo.getDrawingRect(r);
                cuon.offsetDescendantRectToMyCoords(dangGo, r);
                r.bottom += (int) (24 * cuon.getResources().getDisplayMetrics().density);
                cuon.requestChildRectangleOnScreen(dangGo, r, false);
            }
        });
    }

    public interface NhanSo {
        void nhan(int giaTri);
    }

    /**
     * Theo dõi chiều cao bàn phím cho một cửa sổ KHÔNG tự co lại được.
     *
     * Lớp phủ khoá phải giữ cờ FLAG_LAYOUT_IN_SCREEN để che kín cả thanh trạng
     * thái — bỏ cờ đó đi là kéo được thanh thông báo xuống, thủng luôn tác dụng
     * của việc khoá. Nhưng cửa sổ đã ghim full màn hình thì bàn phím không đẩy
     * nó lên, nên phải tự đo lấy chiều cao bàn phím rồi tự chừa chỗ.
     */
    public static void theoDoiBanPhim(final View goc, final NhanSo khiDoi) {
        goc.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    private int truoc = -1;

                    @Override
                    public void onGlobalLayout() {
                        int cao = caoBanPhim(goc);
                        if (cao == truoc) return;
                        truoc = cao;
                        khiDoi.nhan(cao);
                    }
                });
    }

    private static int caoBanPhim(View goc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets ins = goc.getRootWindowInsets();
            if (ins != null) return ins.getInsets(WindowInsets.Type.ime()).bottom;
        }
        // Máy cũ: so khung nhìn thấy được với chiều cao màn hình. Phần khuyết ở
        // đáy chính là bàn phím, miễn là nó đủ lớn để không nhầm với thanh
        // điều hướng.
        android.graphics.Rect r = new android.graphics.Rect();
        goc.getWindowVisibleDisplayFrame(r);
        int caoMan = goc.getRootView().getHeight();
        int duoi = caoMan - r.bottom;
        return duoi > caoMan * 0.15 ? duoi : 0;
    }

    /**
     * Chuẩn hoá giờ người dùng gõ tay. Nhận "1030", "10.30", "10 30", "10:30"
     * và trả về "10:30". Trả chuỗi rỗng nếu không hiểu được.
     */
    public static String chuanHoaGio(String tho) {
        if (tho == null) return "";
        String s = tho.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return "";
        int gio, phut;
        if (s.length() <= 2) {           // "9" hoặc "22"
            gio = Integer.parseInt(s);
            phut = 0;
        } else if (s.length() == 3) {    // "930" = 9:30
            gio = Integer.parseInt(s.substring(0, 1));
            phut = Integer.parseInt(s.substring(1));
        } else {                         // "1030" = 10:30
            gio = Integer.parseInt(s.substring(0, 2));
            phut = Integer.parseInt(s.substring(2, 4));
        }
        if (gio > 23 || phut > 59) return "";
        return LuatGiacNgu.hai(gio) + ":" + LuatGiacNgu.hai(phut);
    }

    /** Bảng chọn giờ của hệ thống, luôn 24 giờ. */
    public static void chonGio(Context ctx, String hienTai, final Nhan xong) {
        int gio = 22, phut = 30;
        String chuan = chuanHoaGio(hienTai);
        if (!chuan.isEmpty()) {
            String[] p = chuan.split(":");
            gio = Integer.parseInt(p[0]);
            phut = Integer.parseInt(p[1]);
        } else {
            Calendar c = Calendar.getInstance();
            gio = c.get(Calendar.HOUR_OF_DAY);
            phut = c.get(Calendar.MINUTE);
        }
        new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker v, int g, int p) {
                xong.nhan(LuatGiacNgu.hai(g) + ":" + LuatGiacNgu.hai(p));
            }
        }, gio, phut, true).show();
    }

    /** Bảng chọn ngày của hệ thống. Vào và ra đều là chuỗi YYYY-MM-DD. */
    public static void chonNgay(Context ctx, String isoHienTai, final Nhan xong) {
        Calendar c = Calendar.getInstance();
        if (isoHienTai != null && isoHienTai.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            String[] p = isoHienTai.split("-");
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        }
        new DatePickerDialog(ctx, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker v, int nam, int thang, int ngay) {
                xong.nhan(nam + "-" + LuatGiacNgu.hai(thang + 1) + "-" + LuatGiacNgu.hai(ngay));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Đêm đang xét là đêm đang diễn ra hay đêm hôm qua.
     *
     * Từ 18:00 tới 06:00 là "đêm nay" — người ta vẫn gọi 2 giờ sáng là đêm nay.
     * Còn ban ngày thì cái đêm mà app đang tính chính là đêm vừa qua, gọi
     * "đêm nay" là sai, đúng như user đã chỉ ra.
     */
    public static boolean demDangDienRa(long luc) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(luc);
        int gio = c.get(Calendar.HOUR_OF_DAY);
        return gio >= LuatGiacNgu.MOC_CHIA_NGAY || gio < 6;
    }
}
