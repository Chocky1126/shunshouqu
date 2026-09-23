package cn.pickup.pocket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extract complete tokens; never turn part of a phone/tracking number into a pickup code. */
public final class BatchParser {
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9-]+");

  private BatchParser() {}

  public static List<String> extract(String raw, List<Station> stations) {
    if (raw == null || raw.length() > 100000) throw new IllegalArgumentException("每次最多粘贴 10 万个字符");
    LinkedHashSet<String> result = new LinkedHashSet<>();
    Matcher matcher = TOKEN.matcher(CodeRules.normalize(raw));
    while (matcher.find()) {
      String token = matcher.group();
      if (token.length() > 32) continue;
      for (Station station : stations)
        if (CodeRules.matches(token, station.formats)) {
          result.add(token);
          break;
        }
      if (result.size() > 200) throw new IllegalArgumentException("每次最多识别 200 个取件码，请分批录入");
    }
    return new ArrayList<>(result);
  }
}
