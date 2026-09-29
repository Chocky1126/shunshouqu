package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ColorOsRegressionTest {
  private Context c;

  @Before
  public void setup() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("pickup.db");
    ClipboardImport.prefs(c).edit().clear().commit();
  }

  @Test
  public void manualClipboardReadCanBeRepeated() {
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      a.onActivity(
          v -> {
            v.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipboardFixtures.text("fixture", "2-1-003"));
            try (ParcelStore s = new ParcelStore(v)) {
              assertEquals("2-1-003", ClipboardImport.read(v, s).codes);
              assertEquals("2-1-003", ClipboardImport.read(v, s).codes);
            } finally {
              v.getSystemService(ClipboardManager.class).clearPrimaryClip();
            }
          });
    }
  }

  @Test
  public void removedBackupHasNoEntry() {
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      onView(withText("导出备份")).check(doesNotExist());
      onView(withText("导入备份")).check(doesNotExist());
    }
  }



  @Test
  public void manualPasteWorksDespiteOldAutomaticClipboardPreferences() {
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      a.onActivity(
          v -> {
            ClipboardManager manager = v.getSystemService(ClipboardManager.class);
            manager.setPrimaryClip(ClipboardFixtures.text("fixture", "001234"));
            try (ParcelStore store = new ParcelStore(v)) {
              ClipboardImport.prefs(v).edit().putBoolean("clipboard_enabled", false).commit();
              assertEquals("001234", ClipboardImport.read(v, store).codes);
              manager.clearPrimaryClip();
              ClipboardImport.Result empty = ClipboardImport.read(v, store);
              assertNull(empty.codes);
              assertTrue(empty.message.contains("读取受限"));
            } finally {
              manager.clearPrimaryClip();
            }
          });
      onView(withText("更多设置")).perform(click());
    }
  }



  @Test
  public void retirementCancelsOldPendingWorkWithoutChangingParcels() {
    try (ParcelStore store = new ParcelStore(c)) {
      Parcel p = store.add("001234");
      Intent intent =
          new Intent("cn.pickup.pocket.DAILY_REMINDER")
              .setComponent(
                  new ComponentName(c.getPackageName(), "cn.pickup.pocket.ReminderReceiver"));
      android.app.PendingIntent old =
          android.app.PendingIntent.getBroadcast(
              c,
              1400,
              intent,
              android.app.PendingIntent.FLAG_UPDATE_CURRENT
                  | android.app.PendingIntent.FLAG_IMMUTABLE);
      ClipboardImport.prefs(c)
          .edit()
          .putBoolean("daily_enabled", true)
          .putLong("scheduled_at", 123)
          .putString("clipboard_seen", "old")
          .commit();
      LegacyCleanup.run(c);
      assertFalse(ClipboardImport.prefs(c).contains("daily_enabled"));
      assertFalse(ClipboardImport.prefs(c).contains("scheduled_at"));
      assertNull(
          android.app.PendingIntent.getBroadcast(
              c,
              1400,
              intent,
              android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE));
      assertEquals(p.id, store.all().get(0).id);
      assertEquals(p.createdAt, store.all().get(0).createdAt);
      LegacyCleanup.run(c);
      assertEquals(1, store.all().size());
    }
  }

  @Test
  public void permissionsAndSettingsExcludeScheduledNotifications() throws Exception {
    String[] requested =
        c.getPackageManager()
            .getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions;
    if (requested != null)
      for (String p : requested) {
        assertNotEquals("android.permission.POST_NOTIFICATIONS", p);
        assertNotEquals("android.permission.RECEIVE_BOOT_COMPLETED", p);
      }
    try (ActivityScenario<FeaturesActivity> a =
        ActivityScenario.launch(new Intent(c, FeaturesActivity.class).putExtra("mode", "more"))) {
      onView(withText("每天有待取件时提醒")).check(doesNotExist());
      onView(withText("发送测试提醒")).check(doesNotExist());
      onView(withText("手动添加指引")).check(doesNotExist());
      onView(withText("打开应用时识别剪贴板")).check(doesNotExist());
    }
  }
}
