package cn.pickup.pocket;

import android.content.Context;
import android.content.SharedPreferences;

/** Tracks the one-time guide without changing parcel data or the database schema. */
final class Onboarding {
  static final String PREFS = "onboarding";
  static final int VERSION = 2;
  private static final String SEEN_VERSION = "seen_version";

  private Onboarding() {}

  static boolean shouldShow(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    return prefs.getInt(SEEN_VERSION, 0) < VERSION;
  }

  static void complete(Context context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(SEEN_VERSION, VERSION)
        .apply();
  }
}
