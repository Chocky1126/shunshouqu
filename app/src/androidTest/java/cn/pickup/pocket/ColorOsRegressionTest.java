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
    c.getSharedPreferences("device_features", Context.MODE_PRIVATE).edit().clear().commit();
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
      c.getSharedPreferences("device_features", Context.MODE_PRIVATE)
          .edit()
          .putBoolean("daily_enabled", true)
          .putLong("scheduled_at", 123)
          .putString("clipboard_seen", "old")
          .commit();
      LegacyCleanup.run(c);
      assertFalse(c.getSharedPreferences("device_features", Context.MODE_PRIVATE).contains("daily_enabled"));
      assertFalse(c.getSharedPreferences("device_features", Context.MODE_PRIVATE).contains("scheduled_at"));
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
