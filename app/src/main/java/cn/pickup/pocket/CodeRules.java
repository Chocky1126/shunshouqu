package cn.pickup.pocket;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CodeRules {
  private CodeRules() {}

  public static String normalize(String raw) {
    if (raw == null) throw new IllegalArgumentException("请输入取件码");
    String value = trimWhitespace(raw);
    StringBuilder normalized = new StringBuilder(value.length());
    for (int offset = 0; offset < value.length(); ) {
      int cp = value.codePointAt(offset);
      offset += Character.charCount(cp);
      if (cp >= 0xff10 && cp <= 0xff19) cp = '0' + cp - 0xff10;
      else if (cp == 0xff0d || cp == 0xfe63 || (cp >= 0x2010 && cp <= 0x2014) || cp == 0x2212)
        cp = '-';
      normalized.appendCodePoint(cp);
    }
    return normalized.toString();
  }

  public static String normalizeFormats(String raw) {
    if (raw == null) throw new IllegalArgumentException("请填写取件码格式");
    Set<String> rules = new LinkedHashSet<>();
    for (String part : raw.split("[\\r\\n,，;；/、]")) {
      String rule = normalize(part).toLowerCase(Locale.ROOT);
      if (rule.isEmpty()) continue;
      if (rule.length() > 32 || !rule.matches("x+(-x+)*")) {
        throw new IllegalArgumentException("格式只能包含 x 和分隔用的 -，每条最多 32 个字符");
      }
      rules.add(rule);
    }
    if (rules.isEmpty()) throw new IllegalArgumentException("请至少填写一种取件码格式");
    if (rules.size() > 10) throw new IllegalArgumentException("每个站点最多设置 10 种格式");
    return String.join("\n", rules);
  }

  public static boolean matches(String raw, String formats) {
    if (raw == null) return false;
    String code = normalize(raw);
    for (String rule : normalizeFormats(formats).split("\n")) {
      if (code.length() != rule.length()) continue;
      boolean matched = true;
      for (int i = 0; i < rule.length(); i++) {
        char expected = rule.charAt(i);
        char actual = code.charAt(i);
        if (expected == 'x' ? actual < '0' || actual > '9' : actual != '-') {
          matched = false;
          break;
        }
      }
      if (matched) return true;
    }
    return false;
  }

  public static String formatFromExample(String raw) {
    String code = normalize(raw);
    if (code.length() > 32 || !code.matches("[0-9]+(-[0-9]+)*"))
      throw new IllegalArgumentException("请输入完整取件码，只包含数字和分隔用的 -，最多 32 个字符");
    return code.replaceAll("[0-9]", "x");
  }

  public static String exampleForFormat(String raw) {
    String rule = normalizeFormats(raw);
    if (rule.indexOf('\n') >= 0) throw new IllegalArgumentException("每行填写一种格式");
    StringBuilder example = new StringBuilder();
    int digit = 1;
    for (char c : rule.toCharArray()) example.append(c == 'x' ? (char) ('0' + digit++ % 10) : c);
    return example.toString();
  }

  public static int compareCodes(String a, String b) {
    String left = normalize(a);
    String right = normalize(b);
    String[] leftParts = left.split("-", -1);
    String[] rightParts = right.split("-", -1);
    for (int i = 0; i < Math.min(leftParts.length, rightParts.length); i++) {
      String l = withoutLeadingZeros(leftParts[i]);
      String r = withoutLeadingZeros(rightParts[i]);
      int result = Integer.compare(l.length(), r.length());
      if (result == 0) result = l.compareTo(r);
      if (result != 0) return result;
    }
    int result = Integer.compare(leftParts.length, rightParts.length);
    return result != 0 ? result : left.compareTo(right);
  }

  private static String withoutLeadingZeros(String value) {
    int offset = 0;
    while (offset < value.length() - 1 && value.charAt(offset) == '0') offset++;
    return value.substring(offset);
  }

  static String trimWhitespace(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isWhitespace(value.codePointAt(start))) {
      start += Character.charCount(value.codePointAt(start));
    }
    while (end > start && isWhitespace(value.codePointBefore(end))) {
      end -= Character.charCount(value.codePointBefore(end));
    }
    return value.substring(start, end);
  }

  private static boolean isWhitespace(int cp) {
    return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85;
  }
}
