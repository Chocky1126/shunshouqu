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
 @Test public void clipboardToggleLivesInMoreAndSurvivesRecreation() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  ClipboardImport.prefs(c).edit().putBoolean("clipboard_enabled",true).commit();
  try(ActivityScenario<FeaturesActivity> a=ActivityScenario.launch(new Intent(c,FeaturesActivity.class).putExtra("mode","more"))) {
   onView(withText("打开应用时识别剪贴板")).perform(scrollTo(),click());
   assertFalse(ClipboardImport.enabled(c));a.recreate();
   onView(withText("打开应用时识别剪贴板")).check(matches(isNotChecked()));
   onView(withText("剪贴板读取帮助")).perform(scrollTo(),click());
   onView(withText("ColorOS 剪贴板读取")).check(matches(isDisplayed()));
   onView(withText("知道了")).perform(click());
  } finally {ClipboardImport.prefs(c).edit().putBoolean("clipboard_enabled",true).commit();}
 }
}
