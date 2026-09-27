package com.jael.claudenet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 在手机本地识别图片中的二维码和文字（ML Kit 离线模型，图片不会上传）。
 */
final class ImageRecognizer {
    interface Callback {
        void onResult(List<String> qrPayloads, String text);

        void onError(Exception error);
    }

    /** 文字识别不需要原图分辨率；限制尺寸避免大照片占满内存。 */
    private static final int MAX_DIMENSION = 2560;

    private ImageRecognizer() {}

    static void recognize(Context context, Uri uri, Callback callback) {
        InputImage image;
        try {
            image = load(context, uri);
        } catch (Exception error) {
            callback.onError(error);
            return;
        }
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC,
                        Barcode.FORMAT_DATA_MATRIX)
                .build());
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        Task<List<Barcode>> barcodes = scanner.process(image);
        Task<Text> text = recognizer.process(image);
        Tasks.whenAllComplete(barcodes, text).addOnCompleteListener(done -> {
            scanner.close();
            recognizer.close();
            if (!barcodes.isSuccessful() && !text.isSuccessful()) {
                Exception error = text.getException() != null
                        ? text.getException() : barcodes.getException();
                callback.onError(error != null ? error : new IllegalStateException("识别失败"));
                return;
            }
            List<String> payloads = new ArrayList<>();
            if (barcodes.isSuccessful()) {
                for (Barcode barcode : barcodes.getResult()) {
                    if (barcode.getRawValue() != null) payloads.add(barcode.getRawValue());
                }
            }
            callback.onResult(payloads, text.isSuccessful() ? text.getResult().getText() : "");
        });
    }

    private static InputImage load(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(context, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法读取图片");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2)
                >= MAX_DIMENSION) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap;
        try (InputStream input = open(context, uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IOException("无法读取图片");
        return InputImage.fromBitmap(bitmap, rotationOf(context, uri));
    }

    private static int rotationOf(Context context, Uri uri) {
        try (InputStream input = open(context, uri)) {
            int orientation = new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception ignored) {
            // 截图通常没有 EXIF
            return 0;
        }
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("无法打开图片");
        return input;
    }
}
