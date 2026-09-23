package cn.pickup.pocket;

import android.app.*;
import android.content.*;
import android.os.Bundle;

/** Exported share gateway; feature screens remain internal. */
public final class ShareActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      ImportPayload p = ImportPayload.share(getIntent());
      Intent next =
          new Intent(this, FeaturesActivity.class)
              .putExtra("mode", "batch")
              .putExtra("source", p.text)
              .putParcelableArrayListExtra("images", p.images)
              .putExtra("externalEntry", true);
      if (!p.images.isEmpty()) {
        ClipData clip =
            new ClipData("待识别图片", new String[] {"image/*"}, new ClipData.Item(p.images.get(0)));
        for (int n = 1; n < p.images.size(); n++) clip.addItem(new ClipData.Item(p.images.get(n)));
        next.setClipData(clip);
        next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }
      startActivity(next);
      finish();
    } catch (RuntimeException error) {
      AlertDialog d =
          new AlertDialog.Builder(this)
              .setTitle("无法读取分享内容")
              .setMessage(
                  error instanceof IllegalArgumentException
                      ? error.getMessage()
                      : "分享内容无效或图片不可读，请重新选择。")
              .setPositiveButton("关闭", (v, w) -> finish())
              .setOnCancelListener(v -> finish())
              .show();
      AppStyle.dialog(d);
    }
  }
}
