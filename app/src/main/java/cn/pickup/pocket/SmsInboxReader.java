package cn.pickup.pocket;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.List;

/** Reads a bounded slice of the SMS inbox only after a user-initiated permission check. */
final class SmsInboxReader {
  private static final int MAX_MESSAGES = 500;

  private SmsInboxReader() {}

  static Result scan(Context context, List<Station> stations, long now) {
    if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("没有短信读取权限");

    ArrayList<String> bodies = new ArrayList<>();
    int checked = 0;
    boolean limited = false;
    String date = Telephony.Sms.DATE;
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                Telephony.Sms.Inbox.CONTENT_URI,
                new String[] {date, Telephony.Sms.BODY},
                date + ">=? AND " + date + "<=?",
                new String[] {
                  String.valueOf(now - 3L * 24 * 60 * 60 * 1000), String.valueOf(now)
                },
                date + " DESC")) {
      if (cursor == null) throw new IllegalStateException("无法读取短信收件箱");
      while (cursor.moveToNext()) {
        if (!SmsCodeExtractor.withinLastThreeDays(cursor.getLong(0), now)) continue;
        if (checked == MAX_MESSAGES) {
          limited = true;
          break;
        }
        checked++;
        String body = cursor.getString(1);
        if (body != null && body.length() <= 10000) bodies.add(body);
      }
    }
    return new Result(SmsCodeExtractor.extract(bodies, stations), checked, limited);
  }

  static final class Result {
    final List<String> codes;
    final int checked;
    final boolean limited;

    Result(List<String> codes, int checked, boolean limited) {
      this.codes = codes;
      this.checked = checked;
      this.limited = limited;
    }
  }
}
