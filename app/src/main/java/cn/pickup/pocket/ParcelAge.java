package cn.pickup.pocket;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class ParcelAge {
  private ParcelAge() {}

  public static long days(long created, long now, ZoneId zone) {
    return Math.max(
        0,
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(created).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()));
  }

  public static long days(long created) {
    return days(created, System.currentTimeMillis(), ZoneId.systemDefault());
  }

  public static String label(long created) {
    long days = days(created);
    return days == 0 ? "今天录入" : "已放 " + days + " 天";
  }
}
