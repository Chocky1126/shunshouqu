package cn.pickup.pocket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Bundled recognition model; bounded decoding and no Activity reference in worker code. */
public final class ScreenshotRecognizer implements AutoCloseable {
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean closed;

  public void read(Context context, Uri uri, Consumer<String> success, Consumer<String> failure) {
    Context app = context.getApplicationContext();
    executor.execute(
        () -> {
          Bitmap bitmap = null;
          TextRecognizer recognizer = null;
          try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
              BitmapFactory.decodeStream(in, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0)
              throw new IllegalArgumentException("无法读取这张图片");
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 3200)
              options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
              bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) throw new IllegalArgumentException("图片解码失败");
            int rotation = 0;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
              int orientation =
                  new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
              if (orientation == 6) rotation = 90;
              else if (orientation == 3) rotation = 180;
              else if (orientation == 8) rotation = 270;
            } catch (java.io.IOException ignored) {
              /* Screenshots normally have no EXIF. */
            }
            if (closed) {
              bitmap.recycle();
              return;
            }
            recognizer =
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            final Bitmap ownedBitmap = bitmap;
            final TextRecognizer ownedRecognizer = recognizer;
            recognizer
                .process(InputImage.fromBitmap(bitmap, rotation))
                .addOnCompleteListener(
                    task -> {
                      ownedRecognizer.close();
                      ownedBitmap.recycle();
                      if (closed) return;
                      if (task.isSuccessful()) success.accept(task.getResult().getText());
                      else failure.accept("识别失败，请换一张清晰截图，或粘贴短信文字。");
                    });
          } catch (Exception | OutOfMemoryError error) {
            if (recognizer != null) recognizer.close();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            main.post(
                () -> {
                  if (!closed) failure.accept("无法识别图片，请重新选择清晰截图，或粘贴短信文字。");
                });
          }
        });
  }

  @Override
  public void close() {
    closed = true;
    executor.shutdownNow();
  }
}
