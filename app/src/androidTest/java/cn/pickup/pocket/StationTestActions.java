package cn.pickup.pocket;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

final class StationTestActions {
  static void recreateForeground() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      android.app.Activity activity = ActivityLifecycleMonitorRegistry.getInstance()
          .getActivitiesInStage(Stage.RESUMED).iterator().next();
      activity.recreate();
    });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }
}
