package cn.pickup.pocket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class SmsCodeExtractor {
  private static final long THREE_DAYS = 3L * 24 * 60 * 60 * 1000;
  private static final String[] PARCEL_WORDS = {
    "取件", "快递", "快件", "包裹", "驿站", "提货", "取货", "代收", "自提"
  };

  private SmsCodeExtractor() {}

  static List<String> extract(List<String> bodies, List<Station> stations) {
    LinkedHashSet<String> codes = new LinkedHashSet<>();
    for (String body : bodies) {
      if (body == null || body.length() > 100000 || !isParcelMessage(body)) continue;
      codes.addAll(BatchParser.extract(body, stations));
      if (codes.size() > 200) throw new IllegalArgumentException("短信中识别到太多取件码，请改用分批录入");
    }
    return new ArrayList<>(codes);
  }

  static boolean withinLastThreeDays(long timestamp, long now) {
    return timestamp >= now - THREE_DAYS && timestamp <= now;
  }

  private static boolean isParcelMessage(String body) {
    if (body.contains("验证码")
        && !body.contains("取件")
        && !body.contains("提货")
        && !body.contains("取货")
        && !body.contains("自提")) return false;
    for (String word : PARCEL_WORDS) if (body.contains(word)) return true;
    return false;
  }
}
