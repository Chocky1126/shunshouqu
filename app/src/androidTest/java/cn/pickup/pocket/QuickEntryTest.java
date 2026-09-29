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
public class QuickEntryTest {
 @Test public void longPressEntryOpensBatchAndSingleTapStillOpensSingleEntry() {
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   onView(withId(R.id.add_code)).perform(longClick());
   onView(withId(R.id.batch_source)).check(matches(isDisplayed()));
   onView(withContentDescription("返回取件清单")).perform(click());
   onView(withId(R.id.add_code)).perform(click());
   onView(withId(R.id.code_input)).check(matches(isDisplayed()));
  }
 }
 @Test public void moreSettingsNoLongerOffersAutomaticClipboardReading() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  try(ActivityScenario<FeaturesActivity> a=ActivityScenario.launch(new Intent(c,FeaturesActivity.class).putExtra("mode","more"))) {
   onView(withText("打开应用时识别剪贴板")).check(doesNotExist());
   onView(withText("剪贴板自动识别")).check(doesNotExist());
  }
 }
}
