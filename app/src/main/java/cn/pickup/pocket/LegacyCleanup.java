package cn.pickup.pocket;

import android.app.*;
import android.content.*;

/** One-time retirement of v1.4 scheduled work; no receiver or new alarm is registered. */
final class LegacyCleanup {
  static void run(Context c) {
    SharedPreferences p = ClipboardImport.prefs(c);
    if (p.getBoolean("v150_cleanup", false)) return;
    Intent old =
        new Intent("cn.pickup.pocket.DAILY_REMINDER")
            .setComponent(
                new ComponentName(c.getPackageName(), "cn.pickup.pocket.ReminderReceiver"));
    PendingIntent pending =
        PendingIntent.getBroadcast(
            c, 1400, old, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    if (pending != null) {
      c.getSystemService(AlarmManager.class).cancel(pending);
      pending.cancel();
    }
    NotificationManager notifications = c.getSystemService(NotificationManager.class);
    notifications.cancel(1400);
    notifications.deleteNotificationChannel("pickup_daily");
    p.edit()
        .remove("daily_enabled")
        .remove("daily_hour")
        .remove("daily_minute")
        .remove("last_reminder_day")
        .remove("scheduled_at")
        .remove("clipboard_seen")
        .putBoolean("v150_cleanup", true)
        .apply();
  }
}
