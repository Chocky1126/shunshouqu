package cn.pickup.pocket;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ParcelAppTest {
    @Before public void clean() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("pickup.db");
        Onboarding.complete(context);
    }
    private void restoreFromHistory(String code) {
        onView(withId(R.id.home_menu)).perform(click());
        onView(withText("已取件")).perform(click());
        onView(withText("恢复 " + code)).perform(scrollTo(), click());
        onView(withText("恢复到待取")).perform(click());
        onView(withContentDescription("返回取件清单")).perform(click());
    }
    private void add(String code) {
        onView(withId(R.id.add_code)).perform(click());
        onView(withId(R.id.code_input)).perform(replaceText(code), closeSoftKeyboard());
        onView(withText("保存取件码")).perform(click());
    }
    private void paste(String value) {
        onView(withId(R.id.code_input)).perform(new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<android.view.View> getConstraints() { return isAssignableFrom(android.widget.EditText.class); }
            public String getDescription() { return "paste original clipboard text"; }
            public void perform(androidx.test.espresso.UiController controller, android.view.View view) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("取件码", value));
                ((android.widget.EditText) view).onTextContextMenuItem(android.R.id.paste);
                controller.loopMainThreadUntilIdle();
            }
        });
        onView(withId(R.id.code_input)).perform(closeSoftKeyboard());
    }
    private void setDeviceRotation(int rotation) {
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("settings put system accelerometer_rotation 0")) {
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("settings put system user_rotation " + rotation)) {
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        SystemClock.sleep(800);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void addsAllThreeFormatsAndPersistsAcrossRecreation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345"); add("1-2-034"); add("001234");
            onView(withId(R.id.total_count)).check(matches(withText("3")));
            activity.recreate();
            onView(withText("1-2-0345")).check(matches(isDisplayed()));
            onView(withText("1-2-034")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("001234")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    @Test public void extendedStationTwoCodeIsGroupedAndCanBeRestored() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("01-2-034");
            onView(withText("01-2-034")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription("删除取件码 01-2-034")).perform(click());
            restoreFromHistory("01-2-034");
            activity.recreate();
            onView(withText("01-2-034")).perform(scrollTo()).check(matches(isDisplayed()));
            activity.onActivity(a -> {
                try (ParcelStore saved = new ParcelStore(a)) {
                    assertEquals(1, saved.all().size());
                    assertEquals(1, saved.all().get(0).stationId);
                }
            });
        }
    }

    @Test public void invalidCodeAndDuplicateCannotCreateExtraParcel() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("001234");
            add("001234");
            onView(withId(R.id.input_status)).check(matches(withText(containsString("已在待取列表"))));
            onView(withId(R.id.code_input)).perform(replaceText("12-3-4567"), closeSoftKeyboard());
            onView(withText("保存取件码")).perform(click());
            onView(withId(R.id.input_status)).check(matches(withText(containsString("格式不匹配"))));
            onView(withText("取消")).perform(click());
            onView(withId(R.id.total_count)).check(matches(withText("1")));
        }
    }
    @Test public void swipeThenHistoryRestoresParcel() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345");
            onView(withContentDescription("取件码 1-2-0345，向左滑动删除")).perform(swipeLeft());
            onView(withId(R.id.total_count)).check(matches(withText("0")));
            restoreFromHistory("1-2-0345");
            onView(withText("1-2-0345")).check(matches(isDisplayed()));
            onView(withId(R.id.total_count)).check(matches(withText("1")));
        }
    }
    @Test public void consecutiveCollectionsCanBeRestoredIndependently() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345"); add("1-2-0346");
            onView(withContentDescription("删除取件码 1-2-0345")).perform(click());
            onView(withContentDescription("删除取件码 1-2-0346")).perform(click());
            restoreFromHistory("1-2-0345");
            onView(withText("1-2-0345")).check(matches(isDisplayed()));
            onView(withId(R.id.total_count)).check(matches(withText("1")));
            restoreFromHistory("1-2-0346");
            onView(withId(R.id.total_count)).check(matches(withText("2")));
        }
    }
    @Test public void stationRenameKeepsRecordsAndSurvivesRestart() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345");
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 站点一"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("小区东门"), closeSoftKeyboard());
            onView(withText("保存站点")).perform(click());
            onView(withText("完成")).perform(click());
            activity.recreate();
            onView(withText("小区东门")).check(matches(isDisplayed()));
            onView(withText("1-2-0345")).check(matches(isDisplayed()));
        }
    }
    @Test public void cannotSaveEmptyStationName() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 站点一")).perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("  "), closeSoftKeyboard());
            onView(withText("保存站点")).perform(click());
            onView(withId(R.id.station_name)).check(matches(isDisplayed()));
            onView(withText("取消")).perform(click());
            onView(withText("完成")).perform(click());
            onView(withText("站点一")).check(matches(isDisplayed()));
        }
    }
    @Test public void fullwidthPasteNormalizesWithoutDiscardingCharacters() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.add_code)).perform(click());
            paste("　１－２－００３４　");
            onView(withText("保存取件码")).perform(click());
            onView(withText("1-2-0034")).check(matches(isDisplayed()));
            onView(withId(R.id.total_count)).check(matches(withText("1")));
        }
    }
    @Test public void pastedLettersCannotBeSilentlyRemovedToMakeValidCode() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.add_code)).perform(click());
            paste("a001234");
            onView(withText("保存取件码")).perform(click());
            onView(withId(R.id.code_input)).check(matches(withText("a001234")));
            onView(withId(R.id.input_status)).check(matches(withText(containsString("格式不匹配"))));
        }
    }
    @Test public void draftCodeSurvivesRotation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.add_code)).perform(click());
            onView(withId(R.id.code_input)).perform(replaceText("1-2-00"), closeSoftKeyboard());
            activity.recreate();
            onView(withId(R.id.code_input)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText("1-2-00")));
        }
    }
    @Test public void draftStationNamesSurviveRotation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 站点一")).perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("小区东门"), closeSoftKeyboard());
            activity.recreate();
            onView(withId(R.id.station_name)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText("小区东门")));
        }
    }
    @Test public void collectedHistoryRemainsAccessibleInLandscape() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ParcelStore fixture = new ParcelStore(context)) {
            for (int n = 1; n <= 6; n++) fixture.add("1-2-000" + n);
        }
        setDeviceRotation(1);
        try {
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                for (int n = 1; n <= 6; n++) {
                    onView(withContentDescription("删除取件码 1-2-000" + n)).perform(scrollTo(), click());
                }
                onView(withId(R.id.add_code)).check(matches(isCompletelyDisplayed()));
                restoreFromHistory("1-2-0006");
                onView(withText("1-2-0006")).perform(scrollTo()).check(matches(isDisplayed()));
            }
        } finally {
            setDeviceRotation(0);
        }
    }
    @Test public void rightAndVerticalGesturesDoNotDeleteParcel() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345");
            onView(withContentDescription("取件码 1-2-0345，向左滑动删除")).perform(swipeRight());
            onView(withId(R.id.total_count)).check(matches(withText("1")));
            onView(withContentDescription("取件码 1-2-0345，向左滑动删除")).perform(swipeUp());
            onView(withText("1-2-0345")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    private void createStation(String name, String formats) {
        onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
        onView(withText("新增站点")).perform(click());
        onView(withId(R.id.station_name)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(R.id.station_formats)).perform(replaceText(formats), closeSoftKeyboard());
        onView(withText("保存站点")).perform(click());
        onView(withText("完成")).perform(click());
    }
    @Test public void customStationIsCreatedMatchedEditedAndPersisted() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            createStation("北门自提点", "xxxxx");
            add("00123");
            onView(withText("00123")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 北门自提点")).perform(scrollTo(), click());
            onView(withId(R.id.station_formats)).perform(replaceText("xxxxx\nxx-xx"), closeSoftKeyboard());
            onView(withText("保存站点")).perform(click());
            onView(withText("完成")).perform(click());
            add("01-23"); activity.recreate();
            onView(withText("01-23")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    @Test public void conflictingFormatDoesNotSaveNewStation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withText("新增站点")).perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("冲突站点"), closeSoftKeyboard());
            onView(withId(R.id.station_formats)).perform(replaceText("xxxxxx"), closeSoftKeyboard());
            onView(withText("保存站点")).perform(click());
            onView(withId(R.id.station_error)).check(matches(withText(containsString("格式"))));
            activity.onActivity(a -> { try (ParcelStore saved = new ParcelStore(a)) { assertEquals(3, saved.stations().size()); } });
        }
    }
    @Test public void occupiedStationCannotBeDeletedButEmptyStationCan() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345");
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 站点一")).perform(click());
            onView(withText("删除站点")).perform(click());
            onView(withId(R.id.station_error)).check(matches(withText(containsString("待取"))));
            onView(withText("取消")).perform(click());
            onView(withContentDescription("编辑站点 站点三")).perform(scrollTo(), click());
            onView(withText("删除站点")).perform(click());
            onView(withText("确认删除")).perform(click());
            onView(withText("完成")).perform(click());
            onView(withText("站点三")).check(doesNotExist());
            onView(withText("1-2-0345")).check(matches(isDisplayed()));
        }
    }
    @Test public void newStationDraftAndFormatsSurviveRotation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withText("新增站点")).perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("新站草稿"), closeSoftKeyboard());
            onView(withId(R.id.station_formats)).perform(replaceText("xxx-xx"), closeSoftKeyboard());
            activity.recreate();
            onView(withId(R.id.station_name)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText("新站草稿")));
            onView(withId(R.id.station_formats)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText("xxx-xx")));
        }
    }
    @Test public void stationCodesSortNumericallyAfterEntryRestartAndHistoryRestore() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("10-1-001"); add("2-1-003"); add("1-3-002"); add("2-1-002");
            assertCodeOrder(activity);
            activity.recreate(); assertCodeOrder(activity);
            onView(withContentDescription("删除取件码 2-1-002")).perform(scrollTo(), click());
            restoreFromHistory("2-1-002");
            assertCodeOrder(activity);
        }
    }
    private void assertCodeOrder(ActivityScenario<MainActivity> activity) {
        activity.onActivity(a -> {
            java.util.List<String> actual = new java.util.ArrayList<>();
            collectCodes(a.findViewById(android.R.id.content), actual);
            assertEquals(java.util.Arrays.asList("1-3-002", "2-1-002", "2-1-003", "10-1-001"), actual);
        });
    }
    private void collectCodes(android.view.View v, java.util.List<String> actual) {
        if (v instanceof android.widget.TextView) {
            String text = ((android.widget.TextView) v).getText().toString();
            if (java.util.Arrays.asList("1-3-002", "2-1-002", "2-1-003", "10-1-001").contains(text)) actual.add(text);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) v;
            for (int n = 0; n < parent.getChildCount(); n++) collectCodes(parent.getChildAt(n), actual);
        }
    }
    @Test public void returningToSettingsThenRotatingDoesNotResurrectOldEditor() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
            onView(withContentDescription("编辑站点 站点一")).perform(click());
            onView(withId(R.id.station_name)).perform(replaceText("东门"), closeSoftKeyboard());
            onView(withText("保存站点")).perform(click());
            activity.recreate();
            onView(withText("完成")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));
            onView(withContentDescription("编辑站点 东门"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(click());
            onView(withText("取消")).perform(click());
            activity.recreate();
            onView(withText("完成")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));
            onView(withContentDescription("编辑站点 站点三"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(scrollTo(), click());
            onView(withText("删除站点")).perform(click());
            onView(withText("确认删除")).perform(click());
            activity.recreate();
            onView(withText("完成")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));
        }
    }
    @Test public void longCustomCodeDisplaysEveryDigit() {
        String value = "12345678901234567890123456789012";
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            createStation("长码站点", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            add(value);
            onView(withText(value)).perform(scrollTo()).check((view, missing) -> {
                if (missing != null) throw missing;
                android.widget.TextView code = (android.widget.TextView) view;
                android.text.Layout layout = code.getLayout();
                assertNotNull(layout);
                assertEquals(value.length(), layout.getLineEnd(layout.getLineCount() - 1));
                for (int line = 0; line < layout.getLineCount(); line++) {
                    assertEquals(0, layout.getEllipsisCount(line));
                    assertTrue("Text fits horizontally", layout.getLineWidth(line) <= code.getWidth() + 1);
                }
                assertTrue("Text fits vertically", layout.getHeight() <= code.getHeight());
            });
        }
    }
    @Test public void collectedHistorySurvivesRotation() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            add("1-2-0345");
            onView(withContentDescription("删除取件码 1-2-0345")).perform(click());
            activity.recreate();
            restoreFromHistory("1-2-0345");
            onView(withId(R.id.total_count)).check(matches(withText("1")));
        }
    }
}
