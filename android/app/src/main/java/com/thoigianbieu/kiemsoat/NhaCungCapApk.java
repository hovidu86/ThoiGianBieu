package com.thoigianbieu.kiemsoat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Cấp tệp APK vừa tải cho trình cài đặt của hệ thống.
 *
 * Từ Android 7, không được đưa đường dẫn file:// cho app khác nữa, phải qua
 * content://. Bản thường dùng là FileProvider của AndroidX, nhưng app này cố ý
 * không phụ thuộc thư viện ngoài nào, nên viết lấy một cái tối giản: chỉ cần mở
 * đọc và trả tên với kích thước là trình cài đặt chạy được.
 */
public class NhaCungCapApk extends ContentProvider {

    public static final String QUYEN = "com.thoigianbieu.kiemsoat.tep";
    private static final String THU_MUC = "capnhat";

    public static File thuMuc(Context ctx) {
        File f = new File(ctx.getCacheDir(), THU_MUC);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static Uri uriCho(String tenTep) {
        return Uri.parse("content://" + QUYEN + "/" + tenTep);
    }

    private File tepCua(Uri uri) {
        String ten = uri.getLastPathSegment();
        if (ten == null || ten.contains("..") || ten.contains("/")) return null;
        return new File(thuMuc(getContext()), ten);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String che) throws FileNotFoundException {
        File f = tepCua(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] cot, String loc, String[] thamSo, String sapXep) {
        File f = tepCua(uri);
        if (f == null || !f.exists()) return null;
        String[] ten = cot != null ? cot
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(ten, 1);
        Object[] hang = new Object[ten.length];
        for (int i = 0; i < ten.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(ten[i])) hang[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(ten[i])) hang[i] = f.length();
        }
        c.addRow(hang);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues gt) {
        throw new UnsupportedOperationException("Chỉ đọc");
    }

    @Override
    public int delete(Uri uri, String loc, String[] thamSo) {
        throw new UnsupportedOperationException("Chỉ đọc");
    }

    @Override
    public int update(Uri uri, ContentValues gt, String loc, String[] thamSo) {
        throw new UnsupportedOperationException("Chỉ đọc");
    }
}
