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
 @Test public void entryOptionsOpenAboveFooterAndSingleTapStillOpensSingleEntry() {
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   a.onActivity(activity -> assertFalse(activity.findViewById(R.id.add_code).isLongClickable()));
   onView(withId(R.id.add_code_options)).perform(click());
   onView(withText("批量录入")).check(matches(isDisplayed()));
   onView(withText("截图识别")).check(matches(isDisplayed()));
   onView(withText("扫描短信")).check(matches(isDisplayed()));
   onView(withText("批量录入")).perform(click());
   onView(withId(R.id.batch_source)).check(matches(isDisplayed()));
   onView(withText("读取剪贴板")).check(doesNotExist());
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
   onView(withText("手动指定站点录入")).check(doesNotExist());
   onView(withText("手动录入")).check(doesNotExist());
  }
 }
 @Test public void manualEntryLivesInHomeMenuAndAcceptsAnUnmatchedCode() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.deleteDatabase("pickup.db");
  Onboarding.complete(c);
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   onView(withId(R.id.home_menu)).perform(click());
   onView(withText("手动录入")).perform(click());
   onView(withId(R.id.edit_code)).perform(replaceText("123"),closeSoftKeyboard());
   onView(withText("保存取件码")).perform(scrollTo(),click());
   onView(withText("123")).check(matches(isDisplayed()));
   try(ParcelStore store=new ParcelStore(c)) { assertEquals("123",store.all().get(0).code); }
  } finally { c.deleteDatabase("pickup.db"); }
 }
}
