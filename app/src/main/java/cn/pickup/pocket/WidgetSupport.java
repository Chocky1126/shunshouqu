package cn.pickup.pocket;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

final class WidgetSupport {
  private WidgetSupport() {}

  static void publishPreview(Context context) {
    if (Build.VERSION.SDK_INT < 35) return;
    android.content.SharedPreferences prefs = context.getSharedPreferences("widget_preview", Context.MODE_PRIVATE);
    if (prefs.getBoolean("v170_published", false)) return;
    try {
      boolean published = AppWidgetManager.getInstance(context).setWidgetPreview(
          new ComponentName(context, PickupWidget.class),
          AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
          new RemoteViews(context.getPackageName(), R.layout.pickup_widget_preview));
      if (published) prefs.edit().putBoolean("v170_published", true).apply();
    } catch (RuntimeException ignored) {
      // The static preview image remains available when a host rejects generated previews.
    }
  }

  static boolean requestPin(Activity activity) {
    AppWidgetManager manager = AppWidgetManager.getInstance(activity);
    return manager.isRequestPinAppWidgetSupported()
        && manager.requestPinAppWidget(new ComponentName(activity, PickupWidget.class), pinExtras(activity), null);
  }

  static Bundle pinExtras(Context context) {
    Bundle extras = new Bundle();
    extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
        new RemoteViews(context.getPackageName(), R.layout.pickup_widget_pin_preview));
    return extras;
  }

  static String manualInstructions() {
    return "长按桌面空白处或双指捏合，打开“小部件”或“插件”，找到“顺手取”，将 4×4 小部件添加到桌面。";
  }
}
