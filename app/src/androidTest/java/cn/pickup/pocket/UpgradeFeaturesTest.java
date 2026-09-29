package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UpgradeFeaturesTest {
  private Context c;

  @Before
  public void clean() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("pickup.db");
    ClipboardImport.prefs(c).edit().clear().commit();
  }

  @After
  public void reset() {
    ClipboardImport.prefs(c).edit().clear().commit();
  }

  @Test
  public void sharedTextUsesPreviewAndRequiresSave() {
    Intent i =
        new Intent(c, FeaturesActivity.class)
            .putExtra("mode", "batch")
            .putExtra("source", "2-1-003 001234 2-1-003");
    try (ActivityScenario<FeaturesActivity> a = ActivityScenario.launch(i)) {
      onView(withText("核对识别结果 · 2 个")).check(matches(isDisplayed()));
      try (ParcelStore s = new ParcelStore(c)) {
        assertTrue(s.all().isEmpty());
      }
      a.recreate();
      onView(withContentDescription("选择取件码 001234")).perform(scrollTo(), click());
      onView(withText("保存勾选的取件码")).perform(closeSoftKeyboard(), scrollTo(), click());
      try (ParcelStore s = new ParcelStore(c)) {
        assertEquals(1, s.all().size());
        assertEquals("2-1-003", s.all().get(0).code);
      }
    }
  }

  private boolean has(View v, String text) {
    if (v instanceof TextView && text.equals(((TextView) v).getText().toString())) return true;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int n = 0; n < g.getChildCount(); n++) if (has(g.getChildAt(n), text)) return true;
    }
    return false;
  }

  @Test
  public void multiImageRotationPartialFailureAndDedup() {
    ArrayList<Uri> uris =
        new ArrayList<>(
            Arrays.asList(
                Uri.parse("content://cn.pickup.pocket.test.images/one"),
                Uri.parse("content://cn.pickup.pocket.test.images/broken"),
                Uri.parse("content://cn.pickup.pocket.test.images/two")));
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(
            new Intent(c, FeaturesActivity.class)
                .putExtra("mode", "batch")
                .putParcelableArrayListExtra("images", uris))) {
      a.recreate();
      long until = SystemClock.elapsedRealtime() + 30000;
      java.util.concurrent.atomic.AtomicBoolean ready =
          new java.util.concurrent.atomic.AtomicBoolean();
      while (SystemClock.elapsedRealtime() < until) {
        a.onActivity(v -> ready.set(has(v.getWindow().getDecorView(), "核对识别结果 · 3 个")));
        if (ready.get()) break;
        SystemClock.sleep(100);
      }
      assertTrue("All readable images must complete", ready.get());
      onView(withText(containsString("1 张图片未能读取")))
          .perform(scrollTo())
          .check(matches(isDisplayed()));
      try (ParcelStore s = new ParcelStore(c)) {
        assertTrue(s.all().isEmpty());
      }
      onView(withText("保存勾选的取件码")).perform(closeSoftKeyboard(), scrollTo(), click());
      try (ParcelStore s = new ParcelStore(c)) {
        assertEquals(3, s.all().size());
      }
    }
  }

  @Test
  public void manualClipboardReadFiltersPendingAndCanBeRepeated() {
    try (ParcelStore s = new ParcelStore(c)) {
      s.add("001234");
    }
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      onView(withText("更多设置")).check(matches(isDisplayed()));
      a.onActivity(
          v -> {
            v.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipboardFixtures.text("fixture", "001234 2-1-003"));
            try (ParcelStore s = new ParcelStore(v)) {
              assertEquals("2-1-003", ClipboardImport.read(v, s).codes);
              assertEquals("2-1-003", ClipboardImport.read(v, s).codes);
            }
            v.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipboardFixtures.text("fixture", "1-2-0034"));
            try (ParcelStore s = new ParcelStore(v)) {
              assertEquals("1-2-0034", ClipboardImport.read(v, s).codes);
            }
            v.getSystemService(ClipboardManager.class).clearPrimaryClip();
          });
      // Dismiss Android's floating clipboard preview before the next ActivityScenario.
      // Clearing the clipboard alone leaves that system window over subsequent click targets.
      onView(withText("更多设置")).perform(click());
    }
  }

  @Test
  public void sensitiveClipboardIsIgnored() {
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      onView(withText("更多设置")).check(matches(isDisplayed()));
      a.onActivity(
          v -> {
            ClipData clip = ClipboardFixtures.text("sensitive", "001234");
            PersistableBundle extras = clip.getDescription().getExtras();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
            v.getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            try (ParcelStore s = new ParcelStore(v)) {
              assertNull(ClipboardImport.read(v, s).codes);
            }
            v.getSystemService(ClipboardManager.class).clearPrimaryClip();
          });
      // Dismiss Android's floating clipboard preview before the next ActivityScenario.
      // Clearing the clipboard alone leaves that system window over subsequent click targets.
      onView(withText("更多设置")).perform(click());
    }
  }



  @Test
  public void externalMultiShareGatewayMergesImages() {
    ArrayList<Uri> images =
        new ArrayList<>(
            Arrays.asList(
                Uri.parse("content://cn.pickup.pocket.test.images/one"),
                Uri.parse("content://cn.pickup.pocket.test.images/two")));
    Intent share =
        new Intent(c, ShareActivity.class)
            .setAction(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/png")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, images)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    c.startActivity(share);
    java.util.concurrent.atomic.AtomicReference<FeaturesActivity> current =
        new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicBoolean ready =
        new java.util.concurrent.atomic.AtomicBoolean();
    long until = SystemClock.elapsedRealtime() + 30000;
    try {
      while (SystemClock.elapsedRealtime() < until) {
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync(
                () -> {
                  for (Activity a :
                      androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                          .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                    if (a instanceof FeaturesActivity) {
                      current.set((FeaturesActivity) a);
                      ready.set(has(a.getWindow().getDecorView(), "核对识别结果 · 3 个"));
                    }
                });
        if (ready.get()) break;
        SystemClock.sleep(100);
      }
      assertTrue("Shared images must arrive in the internal preview", ready.get());
      onView(withText("保存勾选的取件码")).perform(closeSoftKeyboard(), scrollTo(), click());
      try (ParcelStore s = new ParcelStore(c)) {
        assertEquals(3, s.all().size());
      }
    } finally {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                for (Activity a :
                    new ArrayList<>(
                        androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)))
                  a.finish();
              });
    }
  }
}
