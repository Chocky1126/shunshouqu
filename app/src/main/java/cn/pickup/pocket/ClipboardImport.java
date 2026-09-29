package cn.pickup.pocket;

import android.content.*;
import java.util.*;

final class ClipboardImport {
  static final class Result {
    final String codes, message;

    Result(String codes, String message) {
      this.codes = codes;
      this.message = message;
    }
  }

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("device_features", Context.MODE_PRIVATE);
  }

  static Result read(Context c, ParcelStore store) {
    try {
      // Read once while focused. Some OEM permission gates do not expose metadata beforehand.
      ClipData clip = c.getSystemService(ClipboardManager.class).getPrimaryClip();
      if (clip == null || clip.getItemCount() == 0) return unavailable();
      ClipDescription desc = clip.getDescription();
      if (desc != null
          && desc.getExtras() != null
          && desc.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false))
        return new Result(null, "这段内容被系统标记为敏感。需要录入时，请在输入框中手动粘贴并核对。");
      StringBuilder raw = new StringBuilder();
      for (int i = 0; i < clip.getItemCount(); i++) {
        CharSequence text = clip.getItemAt(i).getText();
        if (text == null) continue;
        if (raw.length() + text.length() + 1 > ImportPayload.MAX_TEXT)
          return new Result(null, "文字超过 10 万字符，请分批粘贴。");
        if (raw.length() > 0) raw.append('\n');
        raw.append(text);
      }
      if (raw.toString().trim().isEmpty()) return unavailable();
      List<String> found = BatchParser.extract(raw.toString(), store.stations());
      if (found.isEmpty()) return new Result(null, "没有识别到符合站点格式的取件码。可手动粘贴文字，或调整站点格式。");
      Set<String> existing = new HashSet<>();
      for (Parcel p : store.all()) existing.add(p.code);
      found.removeIf(existing::contains);
      if (found.isEmpty()) return new Result(null, "剪贴板中的取件码已在待取列表中。");
      String codes = String.join("\n", found);
      return new Result(codes, "");
    } catch (IllegalArgumentException e) {
      return new Result(null, e.getMessage());
    } catch (RuntimeException e) {
      return unavailable();
    }
  }

  private static Result unavailable() {
    return new Result(null, "剪贴板为空或读取受限。可长按输入框选择粘贴；ColorOS 用户可在系统设置中搜索“剪贴板”，检查顺手取的读取权限。");
  }

}
