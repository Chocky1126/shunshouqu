package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FeatureUiTest {
  private Context context;

  @Before
  public void clean() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    Onboarding.complete(context);
    context.getSharedPreferences("FeaturesActivity", 0).edit().clear().commit();
  }

  private ActivityScenario<FeaturesActivity> launch(String mode) {
    return ActivityScenario.launch(
        new Intent(context, FeaturesActivity.class).putExtra("mode", mode));
  }

  @Test
  public void batchNeedsPreviewDeduplicatesAndPersistsSelectionOnRotation() {
    try (ParcelStore s = new ParcelStore(context)) {
      s.add("001234");
    }
    try (ActivityScenario<FeaturesActivity> a = launch("batch")) {
      onView(withId(R.id.batch_source))
          .perform(replaceText("取件2-1-003，手机13800138000\n001234\n1-2-0034"), closeSoftKeyboard());
      onView(withText("识别取件码")).perform(scrollTo(), click());
      try (ParcelStore s = new ParcelStore(context)) {
        assertEquals(1, s.all().size());
      }
      onView(withContentDescription("选择取件码 1-2-0034")).perform(scrollTo(), click());
      a.recreate();
      onView(withContentDescription("选择取件码 1-2-0034")).check(matches(isNotChecked()));
      onView(withContentDescription("选择取件码 001234")).check(matches(not(isEnabled())));
      onView(withText("保存勾选的取件码")).perform(scrollTo(), click());
      try (ParcelStore s = new ParcelStore(context)) {
        assertEquals(2, s.all().size());
        assertTrue(s.all().stream().anyMatch(p -> p.code.equals("2-1-003")));
      }
    }
  }

  @Test
  public void changedBatchTextRequiresNewPreview() {
    try (ActivityScenario<FeaturesActivity> a = launch("batch")) {
      onView(withId(R.id.batch_source)).perform(replaceText("001234"), closeSoftKeyboard());
      onView(withText("识别取件码")).perform(scrollTo(), click());
      onView(withId(R.id.batch_source))
          .perform(scrollTo(), replaceText("001235"), closeSoftKeyboard());
      onView(withText("保存勾选的取件码")).perform(scrollTo(), click());
      onView(withText(containsString("文字已修改"))).check(matches(isDisplayed()));
      try (ParcelStore s = new ParcelStore(context)) {
        assertTrue(s.all().isEmpty());
      }
    }
  }

  @Test
  public void manualEditKeepsTimestampAndDraft() {
    Parcel original;
    try (ParcelStore s = new ParcelStore(context)) {
      original = s.add("001234");
    }
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(
            new Intent(context, FeaturesActivity.class)
                .putExtra("mode", "edit")
                .putExtra("parcel", original.id))) {
      onView(withId(R.id.edit_code)).perform(replaceText("123-45"), closeSoftKeyboard());
      a.recreate();
      onView(withId(R.id.edit_code)).check(matches(withText("123-45")));
      onView(withText("保存取件码")).perform(scrollTo(), click());
      try (ParcelStore s = new ParcelStore(context)) {
        assertEquals("123-45", s.all().get(0).code);
        assertEquals(original.createdAt, s.all().get(0).createdAt);
        assertEquals(original.id, s.all().get(0).id);
      }
    }
  }

  @Test
  public void pickupSkipsAdvancesArchivesAndReleasesAwake() {
    try (ParcelStore s = new ParcelStore(context)) {
      s.add("10-1-001");
      s.add("2-1-003");
    }
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(
            new Intent(context, FeaturesActivity.class)
                .putExtra("mode", "pickup")
                .putExtra("station", 1))) {
      onView(withId(R.id.focus_code)).check(matches(withText("2-1-003")));
      onView(withText("跳过，稍后取")).perform(scrollTo(), click());
      a.recreate();
      onView(withId(R.id.focus_code)).check(matches(withText("10-1-001")));
      onView(withText("取件时保持屏幕常亮")).perform(scrollTo(), click());
      a.onActivity(
          v ->
              assertTrue(
                  (v.getWindow().getAttributes().flags
                          & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                      != 0));
      a.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      a.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      onView(withText("已取，查看下一件")).perform(scrollTo(), click());
      onView(withId(R.id.focus_code)).check(matches(withText("2-1-003")));
      onView(withText("已取，查看下一件")).perform(scrollTo(), click());
      onView(withText("本站已全部取完")).check(matches(isDisplayed()));
      a.onActivity(
          v ->
              assertEquals(
                  0,
                  v.getWindow().getAttributes().flags
                      & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
      try (ParcelStore s = new ParcelStore(context)) {
        assertTrue(s.all().isEmpty());
        assertEquals(2, s.history().size());
      }
    }
  }

  @Test
  public void historyRestoresAfterAppRecreation() {
    try (ParcelStore s = new ParcelStore(context)) {
      s.delete(s.add("001234").id);
    }
    try (ActivityScenario<FeaturesActivity> a = launch("history")) {
      a.recreate();
      onView(withText("恢复 001234")).perform(scrollTo(), click());
      onView(withText("恢复到待取")).perform(click());
      try (ParcelStore s = new ParcelStore(context)) {
        assertEquals(1, s.all().size());
        assertTrue(s.history().isEmpty());
      }
    }
  }

  @Test
  public void homeLongPressEditsAndStationOrderPersists() {
    try (ParcelStore s = new ParcelStore(context)) {
      s.add("001234");
    }
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
      onView(withContentDescription("上移站点 站点三")).perform(scrollTo(), click());
      onView(withText("完成")).perform(click());
      a.recreate();
      try (ParcelStore s = new ParcelStore(context)) {
        assertEquals(2, s.stations().get(1).id);
      }
      onView(withText("001234")).perform(scrollTo(), longClick());
      onView(withId(R.id.edit_code)).check(matches(withText("001234")));
    }
  }

  @Test
  public void switchingToManualEntryKeepsTypedCode() {
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.add_code)).perform(click());
      onView(withId(R.id.code_input)).perform(replaceText("123-45"), closeSoftKeyboard());
      onView(withText("手动选站点")).perform(click());
      onView(withId(R.id.edit_code)).check(matches(withText("123-45")));
    }
  }
}
