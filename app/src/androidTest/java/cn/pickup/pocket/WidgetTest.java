package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WidgetTest {
  @Test
  public void compactWidgetRowPutsCodeThenStationBesideAnEmptySquare() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      store.add("2-1-003");
      View root = render(context, PickupWidget.views(context, store), 300, 340);
      View code = root.findViewById(R.id.widget_code_1);
      View station = root.findViewById(R.id.widget_station_1);
      TextView square = root.findViewById(R.id.widget_done_1);
      float density = context.getResources().getDisplayMetrics().density;

      assertTrue("Pickup code precedes station on one line", code.getRight() <= station.getLeft());
      assertTrue("Code and station share a baseline", Math.abs(code.getTop() - station.getTop()) < 16 * density);
      assertEquals("2-1-003", ((TextView) code).getText().toString());
      assertEquals("站点二", ((TextView) station).getText().toString());
      assertEquals("", square.getText().toString());
      assertTrue("Completion control is square", Math.abs(square.getWidth() - square.getHeight()) <= density);
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void shortWidgetShowsOnlyRowsThatFitAndCountsHiddenCodes() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      for (String code : new String[] {"1-1-0001", "2-1-0001", "3-1-0001", "4-1-0001", "5-1-0001", "6-1-0001", "7-1-0001"}) {
        store.add(code);
      }
      java.lang.reflect.Method sizedViews;
      try {
        sizedViews = PickupWidget.class.getDeclaredMethod("views", Context.class, ParcelStore.class, int.class);
      } catch (NoSuchMethodException missing) {
        fail("Widget must choose visible rows from its available height");
        return;
      }
      sizedViews.setAccessible(true);
      View shortWidget = render(context, (RemoteViews) sizedViews.invoke(null, context, store, 250), 300, 250);
      assertEquals(View.VISIBLE, shortWidget.findViewById(R.id.widget_row_3).getVisibility());
      assertEquals(View.GONE, shortWidget.findViewById(R.id.widget_row_4).getVisibility());
      assertEquals("另有 4 件，打开应用查看", text(shortWidget, R.id.widget_footer));
      View footerRow = (View) shortWidget.findViewById(R.id.widget_footer).getParent();
      assertTrue(shortWidget.findViewById(R.id.widget_row_3).getBottom() <= footerRow.getTop());

      View fullWidget = render(context, (RemoteViews) sizedViews.invoke(null, context, store, 340), 300, 340);
      assertEquals(View.VISIBLE, fullWidget.findViewById(R.id.widget_row_6).getVisibility());
      assertEquals("另有 1 件，打开应用查看", text(fullWidget, R.id.widget_footer));
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void launcherPreviewInflatesAsRemoteViews() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    RemoteViews preview = new RemoteViews(context.getPackageName(), R.layout.pickup_widget_preview);
    AtomicReference<View> rendered = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(
        () -> rendered.set(preview.apply(context, new android.widget.FrameLayout(context))));
    assertEquals("顺手取", text(rendered.get(), R.id.widget_preview_title));
    assertEquals("一键取出", text(rendered.get(), R.id.widget_preview_complete_all));
  }

  @Test
  public void providerIsDiscoverableAsFourByFourHomeWidget() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    ComponentName provider = new ComponentName(context, "cn.pickup.pocket.PickupWidget");
    ActivityInfo receiver =
        context
            .getPackageManager()
            .getReceiverInfo(provider, PackageManager.GET_META_DATA);
    assertFalse(receiver.exported);
    assertTrue(receiver.metaData.containsKey(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER));

    List<AppWidgetProviderInfo> installed =
        AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(
            context.getPackageName(), null);
    AppWidgetProviderInfo info =
        installed.stream().filter(item -> provider.equals(item.provider)).findFirst().orElseThrow();
    assertEquals(4, info.targetCellWidth);
    assertEquals(4, info.targetCellHeight);
    assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, info.widgetCategory);
  }

  @Test
  public void moreSettingsShowsWidgetEntry() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (ActivityScenario<FeaturesActivity> ignored =
        ActivityScenario.launch(
            new Intent(context, FeaturesActivity.class).putExtra("mode", "more"))) {
      onView(withText("桌面小部件")).check(matches(isDisplayed()));
      onView(withText("添加到桌面")).check(matches(isDisplayed()));
      onView(withText("添加到桌面")).perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click());
      onView(withText("预览桌面小部件")).check(matches(isDisplayed()));
    }
  }

  @Test
  public void widgetShowsFirstSixCodesInHomeOrderAndReportsRemainder() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      store.add("123456");
      store.add("10-1-001");
      store.add("3-1-0001");
      store.add("001234");
      store.add("2-1-003");
      store.add("1-2-0034");
      store.add("222222");

      java.lang.reflect.Method method =
          PickupWidget.class.getDeclaredMethod("views", Context.class, ParcelStore.class);
      method.setAccessible(true);
      RemoteViews remote = (RemoteViews) method.invoke(null, context, store);
      AtomicReference<View> rendered = new AtomicReference<>();
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> rendered.set(remote.apply(context, new android.widget.FrameLayout(context))));

      assertEquals("7 件待取", text(rendered.get(), R.id.widget_total));
      assertEquals("站点一", text(rendered.get(), R.id.widget_station_1));
      assertEquals("1-2-0034", text(rendered.get(), R.id.widget_code_1));
      assertEquals("3-1-0001", text(rendered.get(), R.id.widget_code_2));
      assertEquals("站点二", text(rendered.get(), R.id.widget_station_3));
      assertEquals("2-1-003", text(rendered.get(), R.id.widget_code_3));
      assertEquals("10-1-001", text(rendered.get(), R.id.widget_code_4));
      int fifth = context.getResources().getIdentifier("widget_code_5", "id", context.getPackageName());
      int sixth = context.getResources().getIdentifier("widget_code_6", "id", context.getPackageName());
      assertTrue("Fifth pickup code exists", fifth != 0);
      assertTrue("Sixth pickup code exists", sixth != 0);
      assertEquals("001234", text(rendered.get(), fifth));
      assertEquals("123456", text(rendered.get(), sixth));
      assertEquals("另有 1 件，打开应用查看", text(rendered.get(), R.id.widget_footer));
      assertEquals(View.GONE, rendered.get().findViewById(R.id.widget_empty).getVisibility());
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void onePickupCodeStartsAtTopInsteadOfExpandingToCenter() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      store.add("001234");
      RemoteViews remote = PickupWidget.views(context, store);
      AtomicReference<View> rendered = new AtomicReference<>();
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
        View root = remote.apply(context, new android.widget.FrameLayout(context));
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(300 * density);
        int height = Math.round(360 * density);
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        rendered.set(root);
      });

      View first = rendered.get().findViewById(R.id.widget_row_1);
      assertEquals("001234", text(rendered.get(), R.id.widget_code_1));
      assertTrue("First code stays in the upper third", first.getBottom() < rendered.get().getHeight() / 3);
      assertEquals(View.GONE, rendered.get().findViewById(R.id.widget_empty).getVisibility());
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void emptyWidgetShowsGuidanceAndHidesPickupRows() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    try (ParcelStore store = new ParcelStore(context)) {
      RemoteViews remote = PickupWidget.views(context, store);
      AtomicReference<View> rendered = new AtomicReference<>();
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> rendered.set(remote.apply(context, new android.widget.FrameLayout(context))));

      assertEquals("0 件待取", text(rendered.get(), R.id.widget_total));
      assertEquals(View.VISIBLE, rendered.get().findViewById(R.id.widget_empty).getVisibility());
      assertEquals(View.GONE, rendered.get().findViewById(R.id.widget_row_1).getVisibility());
      assertEquals("打开顺手取", text(rendered.get(), R.id.widget_footer));
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void completeActionArchivesOnlyRequestedParcel() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    Parcel completed;
    Parcel remaining;
    try (ParcelStore store = new ParcelStore(context)) {
      completed = store.add("001234");
      remaining = store.add("2-1-003");
    }

    Intent completeIntent =
        new Intent("cn.pickup.pocket.action.WIDGET_COMPLETE")
            .setPackage(context.getPackageName())
            .putExtra("parcel_id", completed.id);
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> new PickupWidget().onReceive(context, completeIntent));

    try (ParcelStore store = new ParcelStore(context)) {
      assertEquals(1, store.all().size());
      assertEquals(remaining.id, store.all().get(0).id);
      assertEquals(1, store.history().size());
      assertEquals(completed.id, store.history().get(0).parcelId);
    } finally {
      context.deleteDatabase("pickup.db");
    }
  }

  @Test
  public void pendingDataChangesNotifyWidgetImmediately() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    BlockingQueue<String> changes = new ArrayBlockingQueue<>(16);
    BroadcastReceiver receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context ignored, Intent intent) {
            changes.offer(intent.getAction());
          }
        };
    context.registerReceiver(
        receiver,
        new IntentFilter("cn.pickup.pocket.action.WIDGET_DATA_CHANGED"),
        Context.RECEIVER_NOT_EXPORTED);
    try (ParcelStore store = new ParcelStore(context)) {
      Parcel parcel = store.add("001234");
      assertChanged(changes);
      store.updateParcel(parcel.id, "001235", 2);
      assertChanged(changes);
      store.delete(parcel.id);
      assertChanged(changes);
      HistoryEntry history = store.history().get(0);
      store.restoreHistory(history.id, history.stationId);
      assertChanged(changes);
      Station custom = store.addStation("南门", "xx-xx");
      assertChanged(changes);
      store.updateStation(custom.id, "南门柜", "xx-xx");
      assertChanged(changes);
      java.util.ArrayList<Integer> order = new java.util.ArrayList<>();
      for (Station station : store.stations()) order.add(station.id);
      java.util.Collections.reverse(order);
      store.reorderStations(order);
      assertChanged(changes);
      store.deleteStation(custom.id);
      assertChanged(changes);
      store.addBatch(java.util.Arrays.asList("2-1-003", "1-2-0034"));
      assertChanged(changes);
    } finally {
      context.unregisterReceiver(receiver);
      context.deleteDatabase("pickup.db");
    }
  }

  private static void assertChanged(BlockingQueue<String> changes) throws Exception {
    assertEquals(
        "cn.pickup.pocket.action.WIDGET_DATA_CHANGED", changes.poll(2, TimeUnit.SECONDS));
  }

  private static String text(View root, int id) {
    return ((TextView) root.findViewById(id)).getText().toString();
  }

  private static View render(Context context, RemoteViews views, int widthDp, int heightDp) {
    AtomicReference<View> rendered = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      View root = views.apply(context, new android.widget.FrameLayout(context));
      float density = context.getResources().getDisplayMetrics().density;
      int width = Math.round(widthDp * density);
      int height = Math.round(heightDp * density);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, width, height);
      rendered.set(root);
    });
    return rendered.get();
  }
}
