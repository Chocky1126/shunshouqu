package cn.pickup.pocket;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Layout;
import android.view.Gravity;
import android.widget.TextView;

/** Local home-list appearance; stored independently of parcel data. */
final class CodeAppearance {
  final int size, spacing;
  private static final int[] SIZES = {22, 28, 32, 36};
  private static final int[] PADDING = {4, 10, 18};

  CodeAppearance(int size, int spacing) {
    this.size = size;
    this.spacing = spacing;
  }

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("code_appearance", Context.MODE_PRIVATE);
  }

  static CodeAppearance read(Context c) {
    SharedPreferences p = prefs(c);
    return new CodeAppearance(bounded(p, "size", 1, 3), bounded(p, "spacing", 1, 2));
  }

  private static int bounded(SharedPreferences p, String key, int fallback, int max) {
    try {
      int value = p.getInt(key, fallback);
      return value >= 0 && value <= max ? value : fallback;
    } catch (ClassCastException e) {
      return fallback;
    }
  }

  static void save(Context c, int size, int spacing) {
    if (size < 0 || size > 3 || spacing < 0 || spacing > 2)
      throw new IllegalArgumentException("显示设置超出范围");
    prefs(c).edit().putInt("size", size).putInt("spacing", spacing).remove("indent").apply();
  }

  int rowPadding() {
    return PADDING[spacing];
  }

  void apply(TextView text) {
    int sp = text.length() > 10 ? Math.max(18, SIZES[size] - 8) : SIZES[size];
    text.setTextSize(sp);
    text.setSingleLine(false);
    text.setMaxLines(Integer.MAX_VALUE);
    text.setEllipsize(null);
    text.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE);
    text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
    text.setMinHeight(Math.round((sp + 8) * text.getResources().getDisplayMetrics().scaledDensity));
    text.setLineSpacing(AppStyle.dp(text.getContext(), spacing == 2 ? 4 : 0), 1);
    text.setPadding(characterWidth(text), 0, 0, 0);
    text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
  }

  static int characterWidth(TextView text) {
    return Math.round(text.getPaint().measureText("0"));
  }
}
