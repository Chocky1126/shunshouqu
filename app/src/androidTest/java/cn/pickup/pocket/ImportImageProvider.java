package cn.pickup.pocket;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test APK only: generated receipts for actual content-URI OCR and external shares. */
public final class ImportImageProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public String getType(Uri uri) {
    return "image/png";
  }

  @Override
  public synchronized ParcelFileDescriptor openFile(Uri uri, String mode)
      throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if ("broken".equals(name)) throw new FileNotFoundException("fixture failure");
    if (!"one".equals(name) && !"two".equals(name)) throw new FileNotFoundException();
    File f = new File(getContext().getCacheDir(), "import-" + name + ".png");
    if (!f.exists()) {
      Bitmap image = Bitmap.createBitmap(1080, 700, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(image);
      canvas.drawColor(Color.WHITE);
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setColor(Color.BLACK);
      p.setTextSize(58);
      canvas.drawText("快递已到站，请凭码取件", 70, 140, p);
      p.setTypeface(Typeface.MONOSPACE);
      p.setTextSize(82);
      canvas.drawText("2-1-003", 70, 330, p);
      canvas.drawText("one".equals(name) ? "001234" : "1-2-0034", 70, 520, p);
      // Publish only a complete fixture. Rotation can open the same URI from two workers.
      File pending = new File(getContext().getCacheDir(), "import-" + name + ".tmp");
      try {
        try (OutputStream out = new FileOutputStream(pending)) {
          if (!image.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("PNG encoding failed");
        }
        if (!pending.renameTo(f)) throw new IOException("Could not publish fixture");
      } catch (IOException e) {
        pending.delete();
        throw new FileNotFoundException(e.toString());
      } finally {
        image.recycle();
      }
    }
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String s, String[] args, String order) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues v, String s, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String s, String[] args) {
    throw new UnsupportedOperationException();
  }
}
