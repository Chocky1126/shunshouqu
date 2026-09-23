package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.view.Gravity;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class AppearanceTest {
  @Test
  public void appearanceChoicesPersistAndApplyWithoutTruncatingCodes() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.getSharedPreferences("code_appearance", 0).edit().clear().commit();
    c.deleteDatabase("pickup.db");
    try (ParcelStore s = new ParcelStore(c)) {
      s.add("1-2-0034");
    }
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.home_menu)).perform(click());
      onView(withText("更多设置")).perform(click());
      onView(withContentDescription("取件码字号")).perform(scrollTo(), click());
      onData(is("特大")).perform(click());
      onView(withContentDescription("取件码行距")).perform(scrollTo(), click());
      onData(is("宽松")).perform(click());
      onView(withContentDescription("返回取件清单")).perform(click());
      onView(withText("1-2-0034"))
          .perform(scrollTo())
          .check(
              (v, e) -> {
                if (e != null) throw e;
                TextView t = (TextView) v;
                assertEquals(
                    36, t.getTextSize() / t.getResources().getDisplayMetrics().scaledDensity, .1);
                assertTrue(t.getPaddingLeft() > 0);
                assertEquals(
                    Gravity.CENTER_VERTICAL, t.getGravity() & Gravity.VERTICAL_GRAVITY_MASK);
                assertEquals(t.length(), t.getLayout().getLineEnd(t.getLineCount() - 1));
                assertTrue(
                    t.getLayout().getHeight()
                        <= t.getHeight()
                            - t.getCompoundPaddingTop()
                            - t.getCompoundPaddingBottom());
              });
      a.recreate();
      onView(withId(R.id.home_menu)).perform(click());
      onView(withText("更多设置")).perform(click());
      onView(withContentDescription("取件码字号")).check(matches(withSpinnerText("特大")));
      onView(withContentDescription("取件码行距")).check(matches(withSpinnerText("宽松")));
      onView(withContentDescription("取件码左侧缩进")).check(doesNotExist());
    } finally {
      c.getSharedPreferences("code_appearance", 0).edit().clear().commit();
    }
  }
}
