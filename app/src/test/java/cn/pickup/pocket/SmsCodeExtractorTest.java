package cn.pickup.pocket;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import org.junit.Test;

public class SmsCodeExtractorTest {
  private final List<Station> stations =
      Arrays.asList(
          new Station(0, "楼下驿站", "x-x-xxxx"),
          new Station(1, "小区东门", "x-x-xxx\nxx-x-xxx"),
          new Station(2, "快递柜", "xxxxxx"));

  @Test
  public void scanOmitsPendingAndRecentlyCollectedCodes() {
    List<String> candidates = Arrays.asList("2-1-003", "001234", "1-2-0034");
    assertEquals(
        Arrays.asList("1-2-0034"),
        SmsCodeExtractor.withoutKnown(
            candidates, new HashSet<>(Arrays.asList("2-1-003", "001234"))));
    assertTrue(
        SmsCodeExtractor.withoutKnown(
                Arrays.asList("001234"), new HashSet<>(Arrays.asList("001234")))
            .isEmpty());
  }

  @Test
  public void extractsParcelMessagesButNotLoginCodes() {
    assertEquals(
        Arrays.asList("2-1-003", "001234"),
        SmsCodeExtractor.extract(
            Arrays.asList(
                "【驿站】您的快递已到，请凭取件码 2-1-003 领取",
                "【账号】登录验证码 987654，五分钟内有效",
                "【快递平台】登录验证码 888888，请勿告诉他人",
                "【快递柜】包裹已入柜，提货码 001234",
                "【驿站】取件码 2-1-003 再次提醒"),
            stations));
  }

  @Test
  public void preservesLeadingZeroesAndRejectsPartialTrackingNumbers() {
    assertEquals(
        Arrays.asList("001234", "02-1-003"),
        SmsCodeExtractor.extract(
            Arrays.asList(
                "包裹已到，取件码００１２３４，运单号123456789012345",
                "快件在门岗，取件码02-1-003"),
            stations));
  }

  @Test
  public void threeDayWindowIncludesBoundaryButExcludesFuture() {
    long now = 1_800_000_000_000L;
    assertTrue(SmsCodeExtractor.withinLastThreeDays(now - 3L * 24 * 60 * 60 * 1000, now));
    assertFalse(SmsCodeExtractor.withinLastThreeDays(now - 3L * 24 * 60 * 60 * 1000 - 1, now));
    assertFalse(SmsCodeExtractor.withinLastThreeDays(now + 1, now));
  }
}
