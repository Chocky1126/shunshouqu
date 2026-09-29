package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompleteAllTest {
  @Test
  public void homeCompletesEveryPendingParcelInOneTap() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    Onboarding.complete(context);
    try (ParcelStore store = new ParcelStore(context)) {
      store.add("001234");
      store.add("2-1-003");
    }
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.home_complete_all)).check(matches(isDisplayed())).perform(click());
      try (ParcelStore store = new ParcelStore(context)) {
        assertTrue(store.all().isEmpty());
        assertEquals(2, store.history().size());
      }
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void widgetCompleteAllArchivesCodesBeyondVisibleSix() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      for (int i = 1; i <= 7; i++) store.add(i + "-1-0001");
    }
    Intent intent = new Intent(PickupWidget.ACTION_COMPLETE_ALL).setPackage(context.getPackageName());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> new PickupWidget().onReceive(context, intent));
    try (ParcelStore store = new ParcelStore(context)) {
      assertTrue(store.all().isEmpty());
      assertEquals(7, store.history().size());
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }
}
