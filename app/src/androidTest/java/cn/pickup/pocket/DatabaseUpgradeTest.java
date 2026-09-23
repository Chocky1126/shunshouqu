package cn.pickup.pocket;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseUpgradeTest {
    private Context context;
    @Before public void before() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("pickup.db");
    }
    @After public void after() { context.deleteDatabase("pickup.db"); }

    // Frozen legacy schemas, independent of the current ParcelStore table creation.
    private SQLiteOpenHelper legacy(int version) {
        return new SQLiteOpenHelper(context, "pickup.db", null, version) {
            @Override public void onCreate(SQLiteDatabase db) {
                String extra = version == 2 ? " OR code GLOB '[0-9][0-9]-[0-9]-[0-9][0-9][0-9]'" : "";
                db.execSQL("CREATE TABLE parcels (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, station_id INTEGER NOT NULL, created_at INTEGER NOT NULL CHECK(created_at >= 0), CHECK ((station_id=0 AND code GLOB '[0-9]-[0-9]-[0-9][0-9][0-9][0-9]') OR (station_id=1 AND (code GLOB '[0-9]-[0-9]-[0-9][0-9][0-9]'" + extra + ")) OR (station_id=2 AND code GLOB '[0-9][0-9][0-9][0-9][0-9][0-9]')))" );
                db.execSQL("CREATE TABLE stations (id INTEGER PRIMARY KEY CHECK(id BETWEEN 0 AND 2), name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 30))");
                db.execSQL("INSERT INTO stations VALUES (0, '东门驿站'), (1, '小区便利店'), (2, '快递柜')");
            }
            @Override public void onUpgrade(SQLiteDatabase db, int a, int b) { throw new AssertionError("Frozen legacy"); }
        };
    }

    @Test public void upgradeV1PreservesDataNamesAndHistoricalSequence() { verifyUpgrade(1); }
    @Test public void upgradeV2PreservesDataNamesAndHistoricalSequence() { verifyUpgrade(2); }
    @Test public void emptyV1KeepsHistoricalSequence() { verifyEmptyUpgrade(1); }
    @Test public void emptyV2KeepsHistoricalSequence() { verifyEmptyUpgrade(2); }

    private void verifyUpgrade(int version) {
        try (SQLiteOpenHelper old = legacy(version)) {
            SQLiteDatabase db = old.getWritableDatabase();
            db.execSQL("INSERT INTO parcels VALUES (7,'1-2-0034',0,1000), (8,'1-2-034',1,2000), (9,'001234',2,3000), (99,'123456',2,4000)");
            db.execSQL("DELETE FROM parcels WHERE id=99");
            if (version == 2) db.execSQL("INSERT INTO parcels VALUES (10,'01-2-034',1,3500)");
        }
        try (ParcelStore current = new ParcelStore(context)) {
            List<Parcel> rows = current.all();
            assertEquals(5, current.getReadableDatabase().getVersion());
            assertEquals(version == 1 ? 3 : 4, rows.size());
            Parcel oldest = rows.get(rows.size() - 1);
            assertEquals(7, oldest.id); assertEquals("1-2-0034", oldest.code); assertEquals(1000, oldest.createdAt); assertEquals(0, oldest.stationId);
            Parcel zeros = rows.get(version == 1 ? 0 : 1);
            assertEquals(9, zeros.id); assertEquals("001234", zeros.code); assertEquals(3000, zeros.createdAt); assertEquals(2, zeros.stationId);
            assertMigratedStations(current);
            Parcel next = current.add("12-3-456");
            assertTrue(next.id > 99); assertEquals(1, next.stationId);
            current.delete(next.id); current.restore(next);
            current.updateStation(0, "新名称", "x-x-xxxx/xxx-xx");
        }
        try (ParcelStore reopened = new ParcelStore(context)) {
            assertEquals("12-3-456", reopened.all().get(0).code);
            assertEquals("新名称", reopened.stations().get(0).name);
            assertEquals("x-x-xxxx\nxxx-xx", reopened.stations().get(0).formats);
            assertEquals(0, reopened.stationFor("123-45").id);
        }
    }

    private void verifyEmptyUpgrade(int version) {
        try (SQLiteOpenHelper old = legacy(version)) {
            SQLiteDatabase db = old.getWritableDatabase();
            db.execSQL("INSERT INTO parcels VALUES (99,'123456',2,4000)");
            db.execSQL("DELETE FROM parcels");
        }
        try (ParcelStore current = new ParcelStore(context)) {
            assertEquals(5, current.getReadableDatabase().getVersion());
            assertTrue(current.all().isEmpty());
            assertTrue(current.add("12-3-456").id > 99);
            assertMigratedStations(current);
        }
    }

    private void assertMigratedStations(ParcelStore current) {
        List<Station> stations = current.stations();
        assertEquals(3, stations.size());
        assertEquals(0, stations.get(0).id); assertEquals("东门驿站", stations.get(0).name); assertEquals("x-x-xxxx", stations.get(0).formats);
        assertEquals(1, stations.get(1).id); assertEquals("小区便利店", stations.get(1).name); assertEquals("x-x-xxx\nxx-x-xxx", stations.get(1).formats);
        assertEquals(2, stations.get(2).id); assertEquals("快递柜", stations.get(2).name); assertEquals("xxxxxx", stations.get(2).formats);
    }

    @Test public void freshDatabaseKeepsDefaultsAndPersistsStationSequence() {
        int removedId;
        try (ParcelStore current = new ParcelStore(context)) {
            assertEquals(5, current.getReadableDatabase().getVersion());
            assertEquals("站点一", current.stations().get(0).name);
            assertEquals("x-x-xxx\nxx-x-xxx", current.stations().get(1).formats);
            assertEquals(1, current.add("０１－２－０３４").stationId);
            removedId = current.addStation("临时", "xxx-xx").id;
            current.deleteStation(removedId);
        }
        try (ParcelStore reopened = new ParcelStore(context)) {
            assertTrue(reopened.addStation("新站", "xxx-xx").id > removedId);
        }
    }
}
