package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

/** The compact home menu must retain reachable station and import flows. */
public class PixelNavigationTest {
  private void awaitState(ActivityScenario<?> scenario, Lifecycle.State expected) {
    long deadline = SystemClock.uptimeMillis() + 10000;
    while (scenario.getState() != expected && SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(50);
    }
    assertEquals(expected, scenario.getState());
  }

  @Test public void menuKeepsAllExistingDestinationsAndClosesOnNavigation() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("pickup.db");
    try (ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.home_menu)).perform(click());
      for (String title:new String[]{"粘贴录入","截图识别","已取件","站点设置","更多设置"})
        onView(withText(title)).check(matches(isDisplayed()));
      onView(withText("站点设置")).perform(click());
      onView(withText("完成")).perform(click());
      onView(withId(R.id.add_code)).check(matches(isDisplayed()));
      onView(withId(R.id.home_menu)).perform(click());
      onView(withText("已取件")).perform(click());
      awaitState(a, Lifecycle.State.CREATED);
      onView(withContentDescription("返回取件清单")).perform(click());
      awaitState(a, Lifecycle.State.RESUMED);
      onView(withId(R.id.add_code)).perform(click());
      onView(withId(R.id.code_input)).perform(replaceText("001234"),closeSoftKeyboard());
      onView(withText("保存取件码")).perform(click());
      onView(withText("001234")).check(matches(isDisplayed()));
    }
  }


}
