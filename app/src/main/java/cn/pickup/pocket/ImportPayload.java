package cn.pickup.pocket;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.util.*;

/** Bounded external input. Only content URIs can be passed to the image decoder. */
final class ImportPayload {
  static final int MAX_IMAGES = 10, MAX_TEXT = 100000;
  final String text;
  final ArrayList<Uri> images;

  private ImportPayload(String text, ArrayList<Uri> images) {
    this.text = text;
    this.images = images;
  }

  static ImportPayload share(Intent intent) {
    if (intent == null
        || !(Intent.ACTION_SEND.equals(intent.getAction())
            || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())))
      throw new IllegalArgumentException("请选择分享文字或图片到顺手取。");
    String type = intent.getType();
    if (type == null || !(type.equals("text/plain") || type.startsWith("image/")))
      throw new IllegalArgumentException("目前支持分享纯文字和图片。");
    CharSequence raw = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
    if (raw == null
        && type.equals("text/plain")
        && intent.getClipData() != null
        && intent.getClipData().getItemCount() > 0)
      raw = intent.getClipData().getItemAt(0).getText();
    String text = raw == null ? "" : raw.toString();
    if (text.length() > MAX_TEXT) throw new IllegalArgumentException("文字过长，请分批分享，每批不超过 10 万字符。");
    ArrayList<Uri> images = type.startsWith("image/") ? images(intent) : new ArrayList<>();
    if (text.trim().isEmpty() && images.isEmpty())
      throw new IllegalArgumentException("没有收到可识别的文字或图片。");
    return new ImportPayload(text, images);
  }

  static ArrayList<Uri> images(Intent intent) {
    LinkedHashSet<Uri> out = new LinkedHashSet<>();
    if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
      ArrayList<?> values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (values != null) for (Object value : values) add(out, value);
    } else {
      Object value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (value != null) add(out, value);
    }
    if (intent.getData() != null) add(out, intent.getData());
    ClipData clip = intent.getClipData();
    if (clip != null)
      for (int n = 0; n < clip.getItemCount(); n++) {
        Uri uri = clip.getItemAt(n).getUri();
        if (uri != null) add(out, uri);
      }
    return new ArrayList<>(out);
  }

  private static void add(Set<Uri> out, Object value) {
    if (!(value instanceof Uri) || !"content".equals(((Uri) value).getScheme()))
      throw new IllegalArgumentException("图片地址无效，请通过系统图片选择器重新选择。");
    out.add((Uri) value);
    if (out.size() > MAX_IMAGES) throw new IllegalArgumentException("每次最多识别 10 张图片，请分批选择。");
  }
}
