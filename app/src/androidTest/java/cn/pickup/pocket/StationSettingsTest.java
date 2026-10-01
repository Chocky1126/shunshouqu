package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

public class StationSettingsTest {
  private Context context;
  @Before public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
  }
  @After public void cleanup() { context.deleteDatabase("pickup.db"); }
  private void open() {
    onView(withId(R.id.home_menu)).perform(click());
    onView(withId(R.id.station_settings)).perform(click());
  }

  @Test public void reorderOnlyAppearsInSortModeAndPersists() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open();
      onView(withContentDescription("下移站点 站点一")).check(doesNotExist());
      onView(withText("调整顺序")).perform(click());
      onView(withContentDescription("下移站点 站点一")).perform(click());
      onView(withText("完成排序")).perform(click());
      onView(withContentDescription("下移站点 站点一")).check(doesNotExist());
      try (ParcelStore store = new ParcelStore(context)) {
        assertEquals("站点二", store.stations().get(0).name);
        assertEquals("站点一", store.stations().get(1).name);
      }
    }
  }

  @Test public void exampleGenerationAddsOneRuleAndPreservesExistingParcels() {
    try (ParcelStore store = new ParcelStore(context)) { store.add("1-2-0034"); }
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open();
      onView(withContentDescription("编辑站点 站点一")).perform(click());
      onView(withHint("输入取件码，例如 12-2-542")).perform(scrollTo(), replaceText("１２－２－００５６"), closeSoftKeyboard());
      onView(withText("生成格式")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 2")).check(matches(withText("xx-x-xxxx")));
      onView(withText("保存站点")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) {
        assertEquals("x-x-xxxx\nxx-x-xxxx", store.stations().get(0).formats);
        assertEquals("1-2-0034", store.all().get(0).code);
        assertEquals(0, store.all().get(0).stationId);
      }
    }
  }

  @Test public void conflictsAreShownBeforeSaveAndCannotWrite() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open(); onView(withContentDescription("编辑站点 站点一")).perform(click());
      onView(withContentDescription("取件码格式 1")).perform(scrollTo(), replaceText("xxxxxx"), closeSoftKeyboard());
      onView(withId(R.id.station_error)).perform(scrollTo()).check(matches(withText(containsString("站点三"))));
      onView(withText("保存站点")).check(matches(org.hamcrest.Matchers.not(isEnabled())));
      try (ParcelStore store = new ParcelStore(context)) { assertEquals("x-x-xxxx", store.stations().get(0).formats); }
    }
  }

  @Test public void deletingOneFormatKeepsTheOtherAndMatchingTestUpdates() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open(); onView(withContentDescription("编辑站点 站点二")).perform(click());
      onView(withContentDescription("删除格式 1")).perform(scrollTo(), click());
      onView(withHint("输入取件码测试匹配")).perform(scrollTo(), replaceText("12-2-542"), closeSoftKeyboard());
      onView(withText(containsString("匹配本站点"))).perform(scrollTo()).check(matches(isDisplayed()));
      onView(withHint("输入取件码测试匹配")).perform(scrollTo(), replaceText("2-2-542"), closeSoftKeyboard());
      onView(withText(containsString("不匹配本站点"))).perform(scrollTo()).check(matches(isDisplayed()));
      onView(withText("保存站点")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) { assertEquals("xx-x-xxx", store.stations().get(1).formats); }
    }
  }

  @Test public void newStationCanAddRemoveRowsAndCancelWithoutSaving() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open(); onView(withText("新增站点")).perform(click());
      onView(withId(R.id.station_name)).perform(replaceText("新站"), closeSoftKeyboard());
      onView(withContentDescription("取件码格式 1")).perform(scrollTo(), replaceText("xxx-xx"), closeSoftKeyboard());
      onView(withText("添加格式")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 2")).perform(scrollTo(), replaceText("xx-xx"), closeSoftKeyboard());
      onView(withContentDescription("删除格式 1")).perform(scrollTo(), click());
      onView(withText("保存站点")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) {
        assertEquals("xx-xx", store.stations().get(3).formats);
      }
      onView(withText("新增站点")).perform(click());
      onView(withId(R.id.station_name)).perform(replaceText("不保存"), closeSoftKeyboard());
      pressBack();
      try (ParcelStore store = new ParcelStore(context)) { assertEquals(4, store.stations().size()); }
    }
  }
  @Test public void draftRowsIconAndMatchingSurviveRecreationWithoutWriting() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open(); onView(withText("新增站点")).perform(click());
      onView(withId(R.id.station_name)).perform(replaceText("草稿站"), closeSoftKeyboard());
      onView(withContentDescription("选择图标 公司")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 1")).perform(scrollTo(), replaceText("xxx-xx"), closeSoftKeyboard());
      onView(withText("添加格式")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 2")).perform(scrollTo(), replaceText("xx-xx"), closeSoftKeyboard());
      onView(withId(R.id.station_test_code)).perform(scrollTo(), replaceText("12-34"), closeSoftKeyboard());
      StationTestActions.recreateForeground();
      onView(withId(R.id.station_test_result)).perform(scrollTo()).check(matches(withText("✓ 匹配本站点")));
      onView(withContentDescription("取件码格式 2")).perform(scrollTo()).check(matches(withText("xx-xx")));
      onView(withContentDescription("已选择图标 公司")).perform(scrollTo()).check(matches(isDisplayed()));
      onView(withId(R.id.station_name)).perform(scrollTo()).check(matches(withText("草稿站")));
      try (ParcelStore store = new ParcelStore(context)) { assertEquals(3, store.stations().size()); }
      onView(withText("保存站点")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) {
        Station saved = store.stations().get(3);
        assertEquals("xxx-xx\nxx-xx", saved.formats); assertEquals("office", saved.iconKey);
      }
    }
  }

  @Test public void repeatedAndInvalidExamplesDoNotAppendAndTenIsTheLimit() {
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      open(); onView(withContentDescription("编辑站点 站点一")).perform(click());
      onView(withId(R.id.station_example)).perform(scrollTo(), replaceText("1-2-0034"), closeSoftKeyboard());
      onView(withText("生成格式")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 2")).check(doesNotExist());
      onView(withId(R.id.station_example)).perform(scrollTo(), replaceText("短信 1-2-0034"), closeSoftKeyboard());
      onView(withText("生成格式")).perform(scrollTo(), click());
      onView(withContentDescription("取件码格式 2")).check(doesNotExist());
      for (int n = 0; n < 9; n++) onView(withText("添加格式")).perform(scrollTo(), click());
      onView(withText("添加格式")).check(matches(org.hamcrest.Matchers.not(isEnabled())));
      onView(withContentDescription("取件码格式 10")).perform(scrollTo()).check(matches(isDisplayed()));
      onView(withContentDescription("删除格式 10")).perform(scrollTo(), click());
      onView(withText("添加格式")).perform(scrollTo()).check(matches(isEnabled()));
    }
  }
}
