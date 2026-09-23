package cn.pickup.pocket;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ParcelStoreTest {
    private Context context;
    private ParcelStore store;
    @Before public void setUp() {
        // Run on the isolated test emulator: instrumentation shares the target UID.
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("pickup.db");
        store = new ParcelStore(context);
    }
    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase("pickup.db");
    }

    @Test public void recordsAndCustomRulesSurviveReopening() {
        Parcel first = store.add("\u3000００１２３４\u00a0");
        Parcel second = store.add("１－２－００３４");
        store.updateStation(0, " 楼下驿站 ", "x-x-xxxx / xxx-xx");
        Station custom = store.addStation("南门", "XX-XX");
        store.close(); store = new ParcelStore(context);
        List<Parcel> rows = store.all();
        assertEquals(2, rows.size());
        assertEquals(second.id, rows.get(0).id);
        assertEquals(first.id, rows.get(1).id);
        assertEquals("001234", rows.get(1).code);
        assertEquals(2, rows.get(1).stationId);
        assertEquals(first.createdAt, rows.get(1).createdAt);
        assertEquals("楼下驿站", store.stations().get(0).name);
        assertEquals("x-x-xxxx\nxxx-xx", store.stations().get(0).formats);
        assertEquals(custom.id, store.stationFor("１２－３４").id);
        assertEquals(custom.id, store.add("12-34").stationId);
        assertNull(store.stationFor("12345"));
        assertNull(store.stationFor(null));
    }

    @Test public void rejectsDuplicateCodesAndInvalidAddsWithoutChangingRows() {
        Parcel first = store.add("001234");
        assertThrows(SQLiteConstraintException.class, () -> store.add("００１２３４"));
        assertThrows(IllegalArgumentException.class, () -> store.add("12345"));
        assertThrows(IllegalArgumentException.class, () -> store.add(null));
        assertEquals(1, store.all().size());
        assertEquals(first.id, store.all().get(0).id);
    }

    @Test public void supportsDynamicCrudAndNeverReusesDeletedStationId() {
        Station added = store.addStation("  新站  ", "XX－XX,xx-xx/xxx-xx");
        assertTrue(added.id > 2);
        assertEquals("新站", added.name);
        assertEquals("xx-xx\nxxx-xx", added.formats);
        store.updateStation(added.id, "新名称", "xx-xx");
        assertEquals("新名称", store.stationFor("12-34").name);
        assertNull(store.stationFor("123-45"));
        store.deleteStation(added.id);
        assertNull(store.stationFor("12-34"));
        Station later = store.addStation("新名称", "xx-xx");
        assertTrue(later.id > added.id);
        assertThrows(IllegalArgumentException.class, () -> store.deleteStation(added.id));
        assertThrows(IllegalArgumentException.class, () -> store.updateStation(added.id, "a", "xx-xx"));
    }

    @Test public void conflictingOrInvalidEditsNeverWritePartialChanges() {
        Station station = store.addStation("自提点", "xx-xx");
        assertThrows(IllegalArgumentException.class, () -> store.addStation("重复", "XX－XX"));
        assertThrows(IllegalArgumentException.class, () -> store.updateStation(station.id, "不应保存", "xx-xx\nXXXXXX"));
        assertEquals("自提点", store.stationFor("12-34").name);
        assertEquals("xx-xx", store.stationFor("12-34").formats);
        assertThrows(IllegalArgumentException.class, () -> store.updateStation(station.id, "\u00a0\u3000", "xx-xx"));
        assertThrows(IllegalArgumentException.class, () -> store.updateStation(station.id, "有效", "x--x"));
        String thirtyEmoji = repeat("\ud83d\udce6", 30);
        store.updateStation(station.id, thirtyEmoji, "xx-xx");
        assertThrows(IllegalArgumentException.class, () -> store.updateStation(station.id, thirtyEmoji + "a", "xx-xx"));
        assertEquals(thirtyEmoji, store.stationFor("12-34").name);
        assertEquals(4, store.stations().size());
    }

    @Test public void editingRulesDoesNotReclassifyRowsOrPreventUndo() {
        Parcel original = store.add("001234");
        store.updateStation(2, "柜子", "xxxxx");
        Station other = store.addStation("另一个站", "xxxxxx");
        assertEquals(other.id, store.stationFor("001234").id);
        assertEquals(2, store.all().get(0).stationId);
        store.delete(original.id);
        store.restore(original);
        assertEquals(original.id, store.all().get(0).id);
        assertEquals(2, store.all().get(0).stationId);
        assertEquals(original.createdAt, store.all().get(0).createdAt);
        assertThrows(IllegalArgumentException.class, () -> store.deleteStation(2));
    }

    @Test public void separateDeletedSnapshotsRestoreIndependentlyWithOriginalOrder() {
        Parcel first = store.add("001234");
        Parcel second = store.add("1-2-0034");
        Parcel third = store.add("1-2-034");
        store.delete(first.id); store.delete(second.id);
        store.restore(first);
        assertEquals(2, store.all().size());
        store.restore(second);
        List<Parcel> rows = store.all();
        assertEquals(3, rows.size());
        assertEquals(third.id, rows.get(0).id);
        assertEquals(second.id, rows.get(1).id);
        assertEquals(first.id, rows.get(2).id);
        assertThrows(SQLiteConstraintException.class, () -> store.restore(first));
        assertThrows(IllegalArgumentException.class, () -> store.delete(Long.MAX_VALUE));
    }

    @Test public void restoreNeverReplacesReaddedCodeAndRejectsDeletedStation() {
        Parcel deleted = store.add("001234");
        store.delete(deleted.id);
        Parcel readded = store.add("001234");
        assertNotEquals(deleted.id, readded.id);
        assertThrows(SQLiteConstraintException.class, () -> store.restore(deleted));
        assertEquals(readded.id, store.all().get(0).id);
        assertThrows(IllegalArgumentException.class, () -> store.deleteStation(2));
        store.delete(readded.id); store.deleteStation(2);
        assertThrows(IllegalArgumentException.class, () -> store.restore(deleted));
        assertTrue(store.all().isEmpty());
    }

    @Test public void restoreRejectsInvalidSnapshotAndDatabaseEnforcesForeignKey() {
        assertThrows(IllegalArgumentException.class, () -> store.restore(new Parcel(1, "１２３４５６", 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> store.restore(new Parcel(1, "12--34", 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> store.restore(new Parcel(0, "001234", 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> store.restore(new Parcel(1, "001234", 2, -1)));
        assertThrows(IllegalArgumentException.class, () -> store.restore(null));
        assertThrows(SQLiteConstraintException.class, () -> store.getWritableDatabase().execSQL("INSERT INTO parcels(code,station_id,created_at) VALUES ('001234',999,1)"));
        store.add("001234");
        assertThrows(SQLiteConstraintException.class, () -> store.getWritableDatabase().execSQL("DELETE FROM stations WHERE id=2"));
        assertEquals(1, store.all().size());
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) text.append(value);
        return text.toString();
    }
}
