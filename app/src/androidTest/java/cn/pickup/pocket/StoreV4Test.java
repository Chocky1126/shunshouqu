package cn.pickup.pocket;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StoreV4Test {
  private Context context;
  private ParcelStore store;

  @Before
  public void before() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("pickup.db");
    store = new ParcelStore(context);
  }

  @After
  public void after() {
    if (store != null) store.close();
    context.deleteDatabase("pickup.db");
  }

  @Test
  public void archiveSurvivesRestartAndDuplicateRestoreKeepsHistory() {
    Parcel p = store.add("001234");
    store.delete(p.id);
    assertEquals("站点三", store.history().get(0).stationName);
    store.close();
    store = new ParcelStore(context);
    store.add("001234");
    long hid = store.history().get(0).id;
    assertThrows(SQLiteConstraintException.class, () -> store.restoreHistory(hid, 2));
    assertEquals(1, store.history().size());
  }

  @Test
  public void deletedStationDoesNotDeleteHistoryAndCanRestoreElsewhere() {
    Station s = store.addStation("南门", "xx-xx");
    Parcel p = store.addManual("12-34", s.id);
    store.delete(p.id);
    store.deleteStation(s.id);
    assertEquals("南门", store.history().get(0).stationName);
    store.restoreHistory(store.history().get(0).id, 0);
    assertEquals(0, store.all().get(0).stationId);
    assertTrue(store.history().isEmpty());
  }

  @Test
  public void manualEditBatchOrderReminderAndEmptyBatchAreAtomic() {
    Parcel p = store.addManual("000-1", 0);
    long at = p.createdAt;
    store.updateParcel(p.id, "1-2-0034", 1);
    assertEquals(at, store.all().get(0).createdAt);
    assertThrows(IllegalArgumentException.class, () -> store.addManual("1--2", 0));
    assertThrows(
        SQLiteConstraintException.class, () -> store.addBatch(Arrays.asList("001234", "1-2-0034")));
    assertEquals(1, store.all().size());
    store.addBatch(Collections.emptyList());
    store.reorderStations(Arrays.asList(2, 0, 1));
    store.close();
    store = new ParcelStore(context);
    assertEquals(2, store.stations().get(0).id);
    assertThrows(
        IllegalArgumentException.class, () -> store.reorderStations(Arrays.asList(0, 1, 1)));
    store.setReminderDays(30);
    assertEquals(30, store.reminderDays());
    assertThrows(IllegalArgumentException.class, () -> store.setReminderDays(31));
  }

  @Test
  public void prunesExpiredHistory() {
    Parcel p = store.add("001234");
    store.delete(p.id);
    store
        .getWritableDatabase()
        .execSQL(
            "UPDATE history SET collected_at=?",
            new Object[] {System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000});
    assertTrue(store.history().isEmpty());
  }

  @Test
  public void upgradesFrozenV3WithOrderAndDefaults() {
    store.close();
    context.deleteDatabase("pickup.db");
    try (SQLiteOpenHelper old =
        new SQLiteOpenHelper(context, "pickup.db", null, 3) {
          public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE stations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT"
                    + " NULL,formats TEXT NOT NULL)");
            db.execSQL(
                "CREATE TABLE parcels(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL"
                    + " UNIQUE,station_id INTEGER NOT NULL REFERENCES stations(id),created_at"
                    + " INTEGER NOT NULL)");
            db.execSQL("INSERT INTO stations(id,name,formats) VALUES(8,'旧站','xx-xx')");
            db.execSQL(
                "INSERT INTO parcels(id,code,station_id,created_at) VALUES(12,'12-34',8,123)");
          }

          public void onUpgrade(SQLiteDatabase d, int a, int b) {}
        }) {
      old.getWritableDatabase();
    }
    store = new ParcelStore(context);
    assertEquals(5, store.getReadableDatabase().getVersion());
    assertEquals(8, store.stations().get(0).id);
    assertEquals(12, store.all().get(0).id);
    assertEquals(3, store.reminderDays());
  }
}
