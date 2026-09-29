package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OnboardingTest {
  private Context context;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().clear().commit();
    context.deleteDatabase("pickup.db");
  }

  @After
  public void tearDown() {
    context
        .getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .putInt("seen_version", Onboarding.VERSION)
        .commit();
    context.deleteDatabase("pickup.db");
  }

  @Test
  public void systemBackMarksGuideVersionSeen() {
    Intent intent = new Intent(context, FeaturesActivity.class).putExtra("mode", "guide");
    try (ActivityScenario<FeaturesActivity> ignored = ActivityScenario.launch(intent)) {
      onView(withText("取件码，只留在手机里")).check(matches(isDisplayed()));
      pressBackUnconditionally();
    }
    assertFalse(Onboarding.shouldShow(context));
  }

  @Test
  public void guideNavigatesFivePagesAndSurvivesRotation() {
    Intent intent = new Intent(context, FeaturesActivity.class).putExtra("mode", "guide");
    try (ActivityScenario<FeaturesActivity> scenario = ActivityScenario.launch(intent)) {
      onView(withText("取件码，只留在手机里")).check(matches(isDisplayed()));
      onView(withText("1 / 5")).check(matches(isDisplayed()));
      onView(withContentDescription("第 1 页，共 5 页")).check(doesNotExist());
      onView(withId(R.id.guide_footer)).check(matches(isDisplayed()));
      onView(withId(R.id.guide_content_scroll)).perform(swipeUp());
      onView(withText("使用引导")).check(matches(isDisplayed()));
      onView(withText("1 / 5")).check(matches(isDisplayed()));
      onView(withText("下一步")).check(matches(isDisplayed()));
      onView(withText("下一步")).perform(click());
      onView(withText("下一步")).perform(click());
      onView(withText("多种方式都能录入")).check(matches(isDisplayed()));
      scenario.recreate();
      onView(withText("3 / 5")).check(matches(isDisplayed()));
      onView(withText("上一步")).perform(click());
      onView(withText("先设置常用站点")).check(matches(isDisplayed()));
      onView(withText("下一步")).perform(click());
      onView(withText("下一步")).perform(click());
      onView(withText("下一步")).perform(click());
      onView(withText("取错也能恢复")).check(matches(isDisplayed()));
      onView(withText("开始使用")).check(matches(isDisplayed()));
      onView(withText("跳过引导")).check(matches(isDisplayed()));
    }
  }

  @Test
  public void guideContentHasFiveConcisePages() {
    assertEquals(5, GuideContent.COUNT);
    assertEquals("取件码，只留在手机里", GuideContent.page(0).title);
    assertEquals("先设置常用站点", GuideContent.page(1).title);
    assertEquals("多种方式都能录入", GuideContent.page(2).title);
    assertEquals("到站后快速取件", GuideContent.page(3).title);
    assertEquals("取错也能恢复", GuideContent.page(4).title);
    for (int i = 0; i < GuideContent.COUNT; i++) {
      assertTrue(GuideContent.page(i).items.length <= 3);
    }
    assertTrue(GuideContent.page(4).items[1].startsWith("桌面小部件\n"));
  }

  @Test
  public void newInstallNeedsCurrentGuide() {
    assertTrue(Onboarding.shouldShow(context));
  }

  @Test
  public void legacyCompletedGuideStillNeedsNewGuideOnce() {
    context
        .getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("completed", true)
        .commit();
    assertTrue(Onboarding.shouldShow(context));
    Onboarding.complete(context);
    assertFalse(Onboarding.shouldShow(context));
    assertEquals(
        Onboarding.VERSION,
        context
            .getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE)
            .getInt("seen_version", 0));
  }

  @Test
  public void freshInstallShowsFiveStepsOnce() {
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("取件码，只留在手机里")).check(matches(isDisplayed()));
      onView(withText("1 / 5")).check(matches(isDisplayed()));
      for (int page = 1; page < GuideContent.COUNT; page++) {
        onView(withText("下一步")).perform(click());
      }
      onView(withText("取错也能恢复")).check(matches(isDisplayed()));
      onView(withText("开始使用")).perform(click());
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
    }
    assertFalse(Onboarding.shouldShow(context));

    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
      onView(withText("取件码，只留在手机里")).check(doesNotExist());
    }
  }

  @Test
  public void existingDatabaseShowsNewGuideOnceAfterUpgrade() {
    try (ParcelStore store = new ParcelStore(context)) {
      store.getWritableDatabase();
    }
    context
        .getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("initialized", true)
        .putBoolean("eligible", false)
        .putBoolean("completed", true)
        .putInt("seen_version", 2)
        .commit();

    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("取件码，只留在手机里")).check(matches(isDisplayed()));
      onView(withText("跳过引导")).perform(click());
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
    }
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
      onView(withText("取件码，只留在手机里")).check(doesNotExist());
    }
  }

  @Test
  public void currentGuideStartsOnHome() {
    Onboarding.complete(context);
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
      onView(withText("取件码，只留在手机里")).check(doesNotExist());
    }
  }

  @Test
  public void moreSettingsCanOpenGuideAgain() {
    Onboarding.complete(context);
    Intent intent = new Intent(context, FeaturesActivity.class).putExtra("mode", "more");
    try (ActivityScenario<FeaturesActivity> ignored = ActivityScenario.launch(intent)) {
      onView(withText("使用引导")).perform(androidx.test.espresso.action.ViewActions.scrollTo(), click());
      onView(withText("取件码，只留在手机里")).check(matches(isDisplayed()));
      onView(withText("跳过引导")).perform(click());
    }
  }
}
