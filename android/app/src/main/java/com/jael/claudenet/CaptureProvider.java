package com.jael.claudenet;

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
 * 让系统相机把原图写进应用私有缓存（EXTRA_OUTPUT 需要 content:// 地址）。
 * 只暴露一个固定文件，不导出，仅通过临时 URI 授权给相机应用；识别完即删除。
 */
public final class CaptureProvider extends ContentProvider {
    private static final String AUTHORITY = "com.jael.claudenet.capture";
    private static final String NAME = "capture.jpg";

    static Uri captureUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(NAME).build();
    }

    static File captureFile(Context context) {
        File dir = new File(context.getCacheDir(), "capture");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, NAME);
    }

    static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        captureFile(context).delete();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        if (!NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        return captureFile(getContext());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            File file = fileFor(uri);
            cursor.addRow(new Object[]{NAME, file.length()});
        } catch (FileNotFoundException ignored) {
            // 返回空结果
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
