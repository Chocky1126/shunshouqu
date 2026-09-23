package cn.pickup.pocket;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class ParcelAgeTest {
 @Test public void countsLocalCalendarDaysAndClampsFuture() {
  ZoneId zone=ZoneId.of("Asia/Shanghai");
  long created=ZonedDateTime.of(2026,9,12,23,59,0,0,zone).toInstant().toEpochMilli();
  long now=ZonedDateTime.of(2026,9,13,0,1,0,0,zone).toInstant().toEpochMilli();
  assertEquals(1,ParcelAge.days(created,now,zone)); assertEquals(0,ParcelAge.days(now,created,zone));
 }
 @Test public void dayCountSurvivesDst() {
  ZoneId zone=ZoneId.of("America/New_York");
  long a=LocalDate.of(2026,3,8).atStartOfDay(zone).toInstant().toEpochMilli();
  long b=LocalDate.of(2026,3,9).atStartOfDay(zone).toInstant().toEpochMilli();
  assertEquals(1,ParcelAge.days(a,b,zone));
 }
}
