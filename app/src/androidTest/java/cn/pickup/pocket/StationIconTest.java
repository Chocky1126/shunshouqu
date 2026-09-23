package cn.pickup.pocket;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StationIconTest {
  private Context context;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
  }

  @After
  public void tearDown() {
    context.deleteDatabase("pickup.db");
  }

  @Test
  public void selectedIconPersistsAcrossEditAndRestart() {
    int id;
    try (ParcelStore store = new ParcelStore(context)) {
      Station station = store.addStation("公司前台", "xxx-xxx", "office");
      id = station.id;
      assertEquals("office", station.iconKey);
      store.updateStation(id, "学校门卫", "xxx-xxx", "school");
    }
    try (ParcelStore store = new ParcelStore(context)) {
      Station station = store.stations().stream().filter(s -> s.id == id).findFirst().orElseThrow();
      assertEquals("school", station.iconKey);
      assertThrows(
          IllegalArgumentException.class,
          () -> store.updateStation(id, station.name, station.formats, "unknown"));
    }
  }

  @Test
  public void frozenV4MigrationKeepsTheThreeExistingIconAssignments() {
    try (SQLiteOpenHelper old =
        new SQLiteOpenHelper(context, "pickup.db", null, 4) {
          @Override
          public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE stations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,"
                    + "formats TEXT NOT NULL,sort_order INTEGER NOT NULL UNIQUE)");
            db.execSQL(
                "CREATE TABLE parcels(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL "
                    + "UNIQUE,station_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE "
                    + "RESTRICT,created_at INTEGER NOT NULL)");
            db.execSQL(
                "CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,parcel_id INTEGER NOT "
                    + "NULL,code TEXT NOT NULL,station_id INTEGER NOT NULL,station_name TEXT NOT "
                    + "NULL,created_at INTEGER NOT NULL,collected_at INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            db.execSQL(
                "INSERT INTO stations(id,name,formats,sort_order) VALUES"
                    + "(0,'驿站','x-x-xxxx',0),(1,'门岗','x-x-xxx',1),(2,'柜机','xxxxxx',2)");
          }

          @Override
          public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        }) {
      old.getWritableDatabase();
    }
    try (ParcelStore store = new ParcelStore(context)) {
      assertEquals(5, store.getReadableDatabase().getVersion());
      assertEquals("shop", store.stations().get(0).iconKey);
      assertEquals("gate", store.stations().get(1).iconKey);
      assertEquals("locker", store.stations().get(2).iconKey);
    }
  }

  @Test
  public void stationEditorOffersIconsAndHomeUsesSelection() {
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.home_menu)).perform(click());
      onView(withId(R.id.station_settings)).perform(click());
      onView(withContentDescription("编辑站点 站点一")).perform(scrollTo(), click());
      onView(withContentDescription("选择图标 公司")).perform(scrollTo(), click());
      ignored.recreate();
      onView(withContentDescription("已选择图标 公司")).check(matches(isDisplayed()));
      onView(withText("保存站点")).perform(click());
      onView(withText("完成")).perform(click());
      onView(withContentDescription("站点图标 公司")).check(matches(isDisplayed()));
    }
  }
}
