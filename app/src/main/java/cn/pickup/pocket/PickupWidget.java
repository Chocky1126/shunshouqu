package cn.pickup.pocket;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.res.Configuration;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Four-cell-square home-screen access to the pending pickup list. */
public final class PickupWidget extends AppWidgetProvider {
  static final String ACTION_COMPLETE = "cn.pickup.pocket.action.WIDGET_COMPLETE";
  static final String ACTION_COMPLETE_ALL = "cn.pickup.pocket.action.WIDGET_COMPLETE_ALL";
  static final String ACTION_DATA_CHANGED = "cn.pickup.pocket.action.WIDGET_DATA_CHANGED";
  static final String EXTRA_PARCEL_ID = "parcel_id";
  private static final int LIMIT = 6;
  private static final int WIDGET_CHROME_DP = 98;
  private static final int ROW_DP = 40;
  private static final int DEFAULT_HEIGHT_DP = WIDGET_CHROME_DP + LIMIT * ROW_DP;

  private static final int[] ROWS = {
    R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
    R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6
  };
  private static final int[] STATIONS = {
    R.id.widget_station_1,
    R.id.widget_station_2,
    R.id.widget_station_3,
    R.id.widget_station_4,
    R.id.widget_station_5,
    R.id.widget_station_6
  };
  private static final int[] CODES = {
    R.id.widget_code_1, R.id.widget_code_2, R.id.widget_code_3,
    R.id.widget_code_4, R.id.widget_code_5, R.id.widget_code_6
  };
  private static final int[] DONE = {
    R.id.widget_done_1, R.id.widget_done_2, R.id.widget_done_3,
    R.id.widget_done_4, R.id.widget_done_5, R.id.widget_done_6
  };

  @Override
  public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    try (ParcelStore store = new ParcelStore(context)) {
      for (int id : ids) {
        manager.updateAppWidget(id, viewsForOptions(context, store, manager.getAppWidgetOptions(id)));
      }
    }
  }

  @Override
  public void onAppWidgetOptionsChanged(
      Context context, AppWidgetManager manager, int id, Bundle options) {
    try (ParcelStore store = new ParcelStore(context)) {
      manager.updateAppWidget(id, viewsForOptions(context, store, options));
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (ACTION_DATA_CHANGED.equals(intent.getAction())) {
      refresh(context);
      return;
    }
    if (ACTION_COMPLETE.equals(intent.getAction())) {
      long parcelId = intent.getLongExtra(EXTRA_PARCEL_ID, -1);
      if (parcelId > 0) {
        try (ParcelStore store = new ParcelStore(context)) {
          store.delete(parcelId);
          Toast.makeText(context, "已完成，可在已取件中恢复", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException ignored) {
          // A stale launcher view may briefly retain a row completed elsewhere.
        }
      }
      refresh(context);
      return;
    }
    if (ACTION_COMPLETE_ALL.equals(intent.getAction())) {
      try (ParcelStore store = new ParcelStore(context)) {
        int count = store.completeAll();
        if (count > 0) Toast.makeText(context, "已取出 " + count + " 件，可在已取件中恢复", Toast.LENGTH_SHORT).show();
      } catch (RuntimeException error) {
        android.util.Log.e("PickupWidget", "Complete all failed", error);
        Toast.makeText(context, "一键取出失败，请在应用中重试", Toast.LENGTH_SHORT).show();
      }
      refresh(context);
      return;
    }
    super.onReceive(context, intent);
  }

  static RemoteViews views(Context context, ParcelStore store) {
    return views(context, store, DEFAULT_HEIGHT_DP);
  }

  static RemoteViews views(Context context, ParcelStore store, int heightDp) {
    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.pickup_widget);
    List<Parcel> all = ordered(store);
    Map<Integer, String> stationNames = new HashMap<>();
    for (Station station : store.stations()) stationNames.put(station.id, station.name);

    views.setTextViewText(R.id.widget_total, all.size() + " 件待取");
    PendingIntent openApp = openApp(context);
    views.setOnClickPendingIntent(R.id.widget_header, openApp);
    views.setOnClickPendingIntent(R.id.widget_empty, openApp);
    views.setOnClickPendingIntent(R.id.widget_footer, openApp);
    views.setOnClickPendingIntent(R.id.widget_complete_all, completeAll(context));
    views.setViewVisibility(R.id.widget_complete_all, all.isEmpty() ? View.GONE : View.VISIBLE);
    views.setViewVisibility(R.id.widget_empty, all.isEmpty() ? View.VISIBLE : View.GONE);
    views.setViewVisibility(R.id.widget_filler, all.isEmpty() ? View.GONE : View.VISIBLE);

    int capacity = Math.max(1, Math.min(LIMIT, (heightDp - WIDGET_CHROME_DP) / ROW_DP));
    int shown = Math.min(capacity, all.size());
    for (int index = 0; index < LIMIT; index++) {
      if (index >= shown) {
        views.setViewVisibility(ROWS[index], View.GONE);
        continue;
      }
      Parcel parcel = all.get(index);
      views.setViewVisibility(ROWS[index], View.VISIBLE);
      views.setTextViewText(STATIONS[index], stationNames.get(parcel.stationId));
      views.setTextViewText(CODES[index], parcel.code);
      views.setOnClickPendingIntent(ROWS[index], openApp);
      views.setOnClickPendingIntent(DONE[index], complete(context, parcel.id));
      views.setContentDescription(DONE[index], "完成取件码 " + parcel.code);
    }
    int remaining = all.size() - shown;
    views.setTextViewText(
        R.id.widget_footer, remaining > 0 ? "另有 " + remaining + " 件，打开应用查看" : "打开顺手取");
    return views;
  }

  private static RemoteViews viewsForOptions(Context context, ParcelStore store, Bundle options) {
    if (Build.VERSION.SDK_INT >= 31 && options != null) {
      ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
      if (sizes != null && !sizes.isEmpty()) {
        Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
        for (int rows = 1; rows <= LIMIT; rows++) {
          int height = WIDGET_CHROME_DP + rows * ROW_DP;
          layouts.put(new SizeF(1f, height), views(context, store, height));
        }
        return new RemoteViews(layouts);
      }
    }
    return views(context, store, legacyHeight(context, options));
  }

  private static int legacyHeight(Context context, Bundle options) {
    if (options == null) return DEFAULT_HEIGHT_DP;
    int min = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
    int max = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
    boolean landscape =
        context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    int preferred = landscape ? min : max;
    if (preferred <= 0) preferred = landscape ? max : min;
    return preferred > 0 ? preferred : DEFAULT_HEIGHT_DP;
  }

  private static List<Parcel> ordered(ParcelStore store) {
    List<Parcel> all = store.all();
    List<Parcel> ordered = new ArrayList<>();
    for (Station station : store.stations()) {
      List<Parcel> stationRows = new ArrayList<>();
      for (Parcel parcel : all) if (parcel.stationId == station.id) stationRows.add(parcel);
      stationRows.sort((left, right) -> CodeRules.compareCodes(left.code, right.code));
      ordered.addAll(stationRows);
    }
    return ordered;
  }

  private static PendingIntent openApp(Context context) {
    Intent intent =
        new Intent(context, MainActivity.class)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("pickup://widget/home"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private static PendingIntent complete(Context context, long parcelId) {
    Intent intent =
        new Intent(context, PickupWidget.class)
            .setAction(ACTION_COMPLETE)
            .setData(Uri.parse("pickup://widget/complete/" + parcelId))
            .putExtra(EXTRA_PARCEL_ID, parcelId);
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private static PendingIntent completeAll(Context context) {
    Intent intent =
        new Intent(context, PickupWidget.class)
            .setAction(ACTION_COMPLETE_ALL)
            .setData(Uri.parse("pickup://widget/complete-all"));
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static void refresh(Context context) {
    AppWidgetManager manager = AppWidgetManager.getInstance(context);
    ComponentName provider = new ComponentName(context, PickupWidget.class);
    int[] ids = manager.getAppWidgetIds(provider);
    if (ids.length == 0) return;
    try (ParcelStore store = new ParcelStore(context)) {
      for (int id : ids) {
        manager.updateAppWidget(id, viewsForOptions(context, store, manager.getAppWidgetOptions(id)));
      }
    } catch (RuntimeException error) {
      android.util.Log.w("PickupWidget", "Widget refresh failed", error);
    }
  }
}
