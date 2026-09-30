package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

public class HistoryTicketTest {
  private Context context;

  @Before public void clean() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    Onboarding.complete(context);
  }

  private ActivityScenario<FeaturesActivity> launch() {
    return ActivityScenario.launch(new Intent(context, FeaturesActivity.class).putExtra("mode", "history"));
  }

  @Test public void scrollingToOlderReceiptKeepsClearActionVisibleAndRestoresOnlyThatRecord() {
    try (ParcelStore store = new ParcelStore(context)) {
      for (int i=0; i<12; i++) store.delete(store.add(String.format(Locale.ROOT, "%06d", 100000+i)).id);
    }
    try (ActivityScenario<FeaturesActivity> activity = launch()) {
      final int[] before = {0};
      onView(withText("清空已取件")).check((view, error) -> {
        if (error != null) throw error;
        int[] location = new int[2]; view.getLocationOnScreen(location); before[0] = location[1];
      });
      onView(withContentDescription("恢复 100000")).perform(scrollTo());
      onView(withText("清空已取件")).check(matches(isDisplayed())).check((view, error) -> {
        if (error != null) throw error;
        int[] location = new int[2]; view.getLocationOnScreen(location); assertEquals(before[0], location[1]);
      });
      onView(withContentDescription("恢复 100000")).perform(click());
      onView(withText("取消")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) { assertEquals(12, store.history().size()); }
      onView(withContentDescription("恢复 100000")).perform(scrollTo(), click());
      onView(withText("恢复到待取")).perform(click());
      activity.recreate();
      try (ParcelStore store = new ParcelStore(context)) {
        assertEquals(11, store.history().size());
        assertEquals("100000", store.all().get(0).code);
      }
    }
  }

  @Test public void longCodeAndDeletedStationRemainReadableAndCanBeRestoredElsewhere() {
    String code = "12345678901234567890123456789012";
    String name = "小区东门快递代收点名称较长仍需完整显示";
    try (ParcelStore store = new ParcelStore(context)) {
      Station station = store.addStation(name, "xx-xx", "market");
      store.delete(store.addManual(code, station.id).id);
      store.deleteStation(station.id);
    }
    try (ActivityScenario<FeaturesActivity> activity = launch()) {
      onView(withText(name)).check(matches(isDisplayed()));
      onView(withText(code)).check(matches(isDisplayed())).check((view, error) -> {
        if (error != null) throw error;
        TextView text = (TextView) view;
        assertTrue(text.getLayout().getLineCount() > 1);
        int last = text.getLayout().getLineCount()-1;
        assertEquals(code.length(), text.getLayout().getLineEnd(last));
      });
      onView(withContentDescription("恢复 " + code)).perform(scrollTo(), click());
      onView(withText("恢复到待取")).perform(click());
      try (ParcelStore store = new ParcelStore(context)) {
        assertTrue(store.history().isEmpty());
        assertEquals(code, store.all().get(0).code);
        assertEquals(0, store.all().get(0).stationId);
      }
    }
  }
}
