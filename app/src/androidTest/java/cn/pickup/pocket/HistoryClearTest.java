package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class HistoryClearTest {
  @Test
  public void clearRequiresConfirmationAndPreservesPendingAndStations() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("pickup.db");
    long pendingId;
    try (ParcelStore s = new ParcelStore(c)) {
      s.delete(s.add("001234").id);
      s.delete(s.add("2-1-003").id);
      pendingId = s.add("1-2-0034").id;
    }
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(
            new Intent(c, FeaturesActivity.class).putExtra("mode", "history"))) {
      onView(withText("清空已取件")).perform(click());
      onView(withText("取消")).perform(click());
      try (ParcelStore s = new ParcelStore(c)) {
        assertEquals(2, s.history().size());
      }
      onView(withText("清空已取件")).perform(click());
      onView(withText("确认清空")).perform(click());
      onView(withText("最近还没有已取件记录")).check(matches(isDisplayed()));
      onView(withText("清空已取件")).check(doesNotExist());
      a.recreate();
      onView(withText("最近还没有已取件记录")).check(matches(isDisplayed()));
    }
    try (ParcelStore s = new ParcelStore(c)) {
      assertTrue(s.history().isEmpty());
      assertEquals(1, s.all().size());
      assertEquals(pendingId, s.all().get(0).id);
      assertEquals(3, s.stations().size());
    }
  }
}
