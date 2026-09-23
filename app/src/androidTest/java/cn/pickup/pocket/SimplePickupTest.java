package cn.pickup.pocket;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.*;
import android.view.Gravity;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
public class SimplePickupTest {
 @Test public void pickupCodeTextIsVerticallyCenteredInsideItsMinimumHeight() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("pickup.db");
  try(ParcelStore s=new ParcelStore(c)){s.add("1-2-0034");}
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   onView(withText("1-2-0034")).check((v,e)->{if(e!=null)throw e;TextView t=(TextView)v;assertEquals(Gravity.CENTER_VERTICAL,t.getGravity()&Gravity.VERTICAL_GRAVITY_MASK);});
  }
 }
 @Test public void fixedCharacterInsetsIgnoreOldPreferenceAndHaveNoSetting() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("pickup.db");
  c.getSharedPreferences("code_appearance",0).edit().clear().putInt("indent",3).commit();
  try(ParcelStore s=new ParcelStore(c)){s.add("1-2-0034");}
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   final int[] digit={0};
   onView(withText("1-2-0034")).check((v,e)->{if(e!=null)throw e;TextView t=(TextView)v;digit[0]=Math.round(t.getPaint().measureText("0"));assertEquals(digit[0],t.getPaddingLeft());});
   onView(withContentDescription("删除取件码 1-2-0034")).check((v,e)->{if(e!=null)throw e;assertEquals(digit[0],((LinearLayout.LayoutParams)v.getLayoutParams()).getMarginEnd());});
   onView(withId(R.id.home_menu)).perform(click());onView(withText("更多设置")).perform(click());
   onView(withContentDescription("取件码左侧缩进")).check(doesNotExist());
  }finally{c.getSharedPreferences("code_appearance",0).edit().clear().commit();}
 }
 @Test public void collectionHasNoUndoBarButHistoryStillRestores() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("pickup.db");
  try(ParcelStore s=new ParcelStore(c)){s.add("1-2-0034");}
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)) {
   onView(withContentDescription("删除取件码 1-2-0034")).perform(click());
   onView(withText("撤销")).check(doesNotExist());a.recreate();
   onView(withId(R.id.total_count)).check(matches(withText("0")));
   onView(withId(R.id.home_menu)).perform(click());onView(withText("已取件")).perform(click());
   onView(withText("恢复 1-2-0034")).perform(scrollTo(),click());onView(withText("恢复到待取")).perform(click());
   onView(withContentDescription("返回取件清单")).perform(click());
   onView(withText("1-2-0034")).check(matches(isDisplayed()));
  }
 }
}
