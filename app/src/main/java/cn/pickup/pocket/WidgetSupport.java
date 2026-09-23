package cn.pickup.pocket;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;

final class WidgetSupport {
  private WidgetSupport() {}

  static boolean requestPin(Activity activity) {
    AppWidgetManager manager = AppWidgetManager.getInstance(activity);
    return manager.isRequestPinAppWidgetSupported()
        && manager.requestPinAppWidget(new ComponentName(activity, PickupWidget.class), null, null);
  }

  static String manualInstructions() {
    return "长按桌面空白处或双指捏合，打开“小部件”或“插件”，找到“顺手取”，拖动 4×4 小部件到桌面。"
        + "ColorOS 的“全部卡片”可能只显示平台专有卡片，请进入普通 Android 小部件列表查找。";
  }
}
