package cn.pickup.pocket;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class ParcelStore extends SQLiteOpenHelper {
  private static final String[] DEFAULT_FORMATS = {"x-x-xxxx", "x-x-xxx\nxx-x-xxx", "xxxxxx"};
  private static final long RETENTION = 7L * 24 * 60 * 60 * 1000;
  private static final int MAX_STATION_ID = Integer.MAX_VALUE - 100_000;
  private static final long MAX_ROW_ID = (1L << 53) - 1;
  private final Context context;

  public ParcelStore(Context c) {
    super(c.getApplicationContext(), "pickup.db", null, 5);
    context = c.getApplicationContext();
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    createStations(db, true, true);
    createParcels(db);
    createHistory(db);
    createSettings(db);
    for (int i = 0; i < 3; i++) {
      ContentValues v =
          sv(
              "站点" + "一二三".substring(i, i + 1),
              DEFAULT_FORMATS[i],
              StationIcons.defaultForId(i));
      v.put("id", i);
      v.put("sort_order", i);
      db.insertOrThrow("stations", null, v);
    }
    setting(db, "reminder_days", "3");
  }

  private static void createStations(SQLiteDatabase db, boolean ordered, boolean icons) {
    db.execSQL(
        "CREATE TABLE stations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL"
            + " CHECK(length(name) BETWEEN 1 AND 30),formats TEXT NOT NULL"
            + (ordered ? ",sort_order INTEGER NOT NULL UNIQUE" : "")
            + (icons
                ? ",icon_key TEXT NOT NULL CHECK(icon_key IN ('shop','gate','locker','home',"
                    + "'office','school','market','parcel'))"
                : "")
            + ")");
  }

  private static void createParcels(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE parcels(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL"
            + " UNIQUE,station_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE"
            + " RESTRICT,created_at INTEGER NOT NULL CHECK(created_at>=0))");
  }

  private static void createHistory(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,parcel_id INTEGER NOT NULL,code"
            + " TEXT NOT NULL,station_id INTEGER NOT NULL,station_name TEXT NOT NULL,created_at"
            + " INTEGER NOT NULL CHECK(created_at>=0),collected_at INTEGER NOT NULL"
            + " CHECK(collected_at>=0))");
  }

  private static void createSettings(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int now) {
    if (old < 1 || old > 4 || now != 5)
      throw new SQLiteException("不支持的数据版本：" + old + " → " + now);
    if (old < 3) migrate3(db);
    if (old < 4) {
      if (seq(db, "stations") > MAX_STATION_ID || seq(db, "parcels") > MAX_ROW_ID)
        throw new SQLiteException("旧数据编号超出安全范围");
      db.execSQL("ALTER TABLE stations ADD COLUMN sort_order INTEGER");
      db.execSQL("UPDATE stations SET sort_order=id");
      db.execSQL("CREATE UNIQUE INDEX stations_sort_order ON stations(sort_order)");
      createHistory(db);
      createSettings(db);
      setting(db, "reminder_days", "3");
    }
    db.execSQL(
        "ALTER TABLE stations ADD COLUMN icon_key TEXT NOT NULL DEFAULT 'shop'"
            + " CHECK(icon_key IN ('shop','gate','locker','home','office','school','market','parcel'))");
    db.execSQL(
        "UPDATE stations SET icon_key=CASE (id % 3) WHEN 1 THEN 'gate' WHEN 2 THEN 'locker'"
            + " ELSE 'shop' END");
  }

  private static void migrate3(SQLiteDatabase db) {
    long ps = seq(db, "parcels"), ss = seq(db, "stations");
    db.execSQL("ALTER TABLE parcels RENAME TO parcels_legacy");
    db.execSQL("ALTER TABLE stations RENAME TO stations_legacy");
    createStations(db, false, false);
    try (Cursor c =
        db.query("stations_legacy", new String[] {"id", "name"}, null, null, null, null, "id")) {
      while (c.moveToNext()) {
        int id = c.getInt(0);
        if (id < 0 || id > 2) throw new SQLiteException("旧站点编号无效：" + id);
        ContentValues v = sv(c.getString(1), DEFAULT_FORMATS[id], null);
        v.remove("icon_key");
        v.put("id", id);
        db.insertOrThrow("stations", null, v);
      }
    }
    createParcels(db);
    db.execSQL("INSERT INTO parcels SELECT id,code,station_id,created_at FROM parcels_legacy");
    db.execSQL("DROP TABLE parcels_legacy");
    db.execSQL("DROP TABLE stations_legacy");
    preserve(db, "parcels", ps);
    preserve(db, "stations", ss);
  }

  private static long seq(SQLiteDatabase db, String table) {
    try (Cursor c =
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name=?", new String[] {table})) {
      return c.moveToFirst() ? c.getLong(0) : 0;
    }
  }

  private static void preserve(SQLiteDatabase db, String table, long value) {
    ContentValues v = new ContentValues();
    v.put("seq", Math.max(value, seq(db, table)));
    if (db.update("sqlite_sequence", v, "name=?", new String[] {table}) == 0) {
      v.put("name", table);
      db.insertOrThrow("sqlite_sequence", null, v);
    }
  }

  public List<Station> stations() {
    List<Station> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "stations",
                new String[] {"id", "name", "formats", "icon_key"},
                null,
                null,
                null,
                null,
                "sort_order")) {
      while (c.moveToNext())
        out.add(new Station(c.getInt(0), c.getString(1), c.getString(2), c.getString(3)));
    }
    return out;
  }

  public Station stationFor(String raw) {
    if (raw == null) return null;
    for (Station s : stations()) if (CodeRules.matches(raw, s.formats)) return s;
    return null;
  }

  public Station addStation(String rn, String rf) {
    return addStation(rn, rf, "shop");
  }

  public Station addStation(String rn, String rf, String iconKey) {
    String n = name(rn), f = CodeRules.normalizeFormats(rf);
    String icon = StationIcons.checked(iconKey);
    SQLiteDatabase db = getWritableDatabase();
    Station added;
    db.beginTransaction();
    try {
      conflict(f, -1);
      ContentValues v = sv(n, f, icon);
      v.put("sort_order", nextOrder(db));
      long id = db.insertOrThrow("stations", null, v);
      if (id > Integer.MAX_VALUE) throw new SQLiteException("站点编号已达到上限");
      db.setTransactionSuccessful();
      added = new Station((int) id, n, f, icon);
    } finally {
      db.endTransaction();
    }
    notifyWidget();
    return added;
  }

  public void updateStation(int id, String rn, String rf) {
    String n = name(rn), f = CodeRules.normalizeFormats(rf);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, id);
      conflict(f, id);
      ContentValues values = sv(n, f, null);
      values.remove("icon_key");
      if (db.update("stations", values, "id=?", a(id)) != 1) throw missingStation();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void updateStation(int id, String rn, String rf, String iconKey) {
    String n = name(rn), f = CodeRules.normalizeFormats(rf);
    String icon = StationIcons.checked(iconKey);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, id);
      conflict(f, id);
      if (db.update("stations", sv(n, f, icon), "id=?", a(id)) != 1) throw missingStation();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void deleteStation(int id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, id);
      try (Cursor c = db.rawQuery("SELECT 1 FROM parcels WHERE station_id=? LIMIT 1", a(id))) {
        if (c.moveToFirst()) throw new IllegalArgumentException("该站点还有待取件，请先取完后再删除");
      }
      if (db.delete("stations", "id=?", a(id)) != 1) throw missingStation();
      reindex(db);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void reorderStations(List<Integer> ids) {
    if (ids == null) throw new IllegalArgumentException("站点顺序不能为空");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      Set<Integer> want = new HashSet<>(ids), have = new HashSet<>();
      for (Station s : stations()) have.add(s.id);
      if (ids.size() != have.size() || want.size() != ids.size() || !want.equals(have))
        throw new IllegalArgumentException("站点顺序必须包含全部站点");
      db.execSQL("UPDATE stations SET sort_order=-sort_order-1");
      for (int i = 0; i < ids.size(); i++) {
        ContentValues v = new ContentValues();
        v.put("sort_order", i);
        db.update("stations", v, "id=?", a(ids.get(i)));
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  private void conflict(String formats, int except) {
    Set<String> p = new HashSet<>(Arrays.asList(formats.split("\n")));
    for (Station s : stations())
      if (s.id != except)
        for (String f : s.formats.split("\n"))
          if (p.contains(f))
            throw new IllegalArgumentException("格式 " + f + " 已被「" + s.name + "」使用");
  }

  private static void station(SQLiteDatabase db, int id) {
    try (Cursor c = db.rawQuery("SELECT 1 FROM stations WHERE id=?", a(id))) {
      if (!c.moveToFirst()) throw missingStation();
    }
  }

  private static IllegalArgumentException missingStation() {
    return new IllegalArgumentException("该站点已不存在");
  }

  private static String name(String raw) {
    if (raw == null) throw new IllegalArgumentException("站点名称不能为空");
    String n = CodeRules.trimWhitespace(raw);
    int count = n.codePointCount(0, n.length());
    if (count < 1 || count > 30 || n.indexOf('\0') >= 0)
      throw new IllegalArgumentException("站点名称需为 1–30 个字符");
    return n;
  }

  private static String code(String raw) {
    String c = CodeRules.normalize(raw);
    if (c.length() < 1 || c.length() > 32 || !c.matches("[0-9]+(-[0-9]+)*"))
      throw new IllegalArgumentException("取件码只能包含数字和分隔用的 -，长度为 1–32 个字符");
    return c;
  }

  public Parcel add(String raw) {
    String c = CodeRules.normalize(raw);
    Station s = stationFor(c);
    if (s == null) throw new IllegalArgumentException("没有匹配的站点，请检查取件码或在站点设置中添加格式");
    return addManual(c, s.id);
  }

  public Parcel addManual(String raw, int sid) {
    String c = code(raw);
    SQLiteDatabase db = getWritableDatabase();
    Parcel added;
    db.beginTransaction();
    try {
      station(db, sid);
      long at = System.currentTimeMillis(), id = db.insertOrThrow("parcels", null, pv(c, sid, at));
      db.setTransactionSuccessful();
      added = new Parcel(id, c, sid, at);
    } finally {
      db.endTransaction();
    }
    notifyWidget();
    return added;
  }

  private void notifyWidget() {
    context.sendBroadcast(
        new Intent(PickupWidget.ACTION_DATA_CHANGED).setPackage(context.getPackageName()));
  }

  public void addBatch(List<String> raws) {
    if (raws == null) throw new IllegalArgumentException("批量内容不能为空");
    List<String> cs = new ArrayList<>();
    List<Integer> ss = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String raw : raws) {
      String c = CodeRules.normalize(raw);
      Station s = stationFor(c);
      if (s == null) throw new IllegalArgumentException("没有匹配的站点：" + c);
      if (!seen.add(c)) throw new IllegalArgumentException("批量内容包含重复取件码：" + c);
      cs.add(c);
      ss.add(s.id);
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      long at = System.currentTimeMillis();
      for (int i = 0; i < cs.size(); i++)
        db.insertOrThrow("parcels", null, pv(cs.get(i), ss.get(i), at));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void updateParcel(long id, String raw, int sid) {
    String c = code(raw);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, sid);
      ContentValues v = new ContentValues();
      v.put("code", c);
      v.put("station_id", sid);
      if (db.update("parcels", v, "id=?", a(id)) != 1)
        throw new IllegalArgumentException("该取件记录已不存在");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public List<Parcel> all() {
    List<Parcel> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "parcels",
                new String[] {"id", "code", "station_id", "created_at"},
                null,
                null,
                null,
                null,
                "created_at DESC,id DESC")) {
      while (c.moveToNext())
        out.add(new Parcel(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3)));
    }
    return out;
  }

  public void delete(long id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      try (Cursor c =
          db.rawQuery(
              "SELECT p.id,p.code,p.station_id,p.created_at,s.name FROM parcels p JOIN stations s"
                  + " ON s.id=p.station_id WHERE p.id=?",
              a(id))) {
        if (!c.moveToFirst()) throw new IllegalArgumentException("该取件记录已不存在");
        db.insertOrThrow(
            "history",
            null,
            hv(
                c.getLong(0),
                c.getString(1),
                c.getInt(2),
                c.getString(4),
                c.getLong(3),
                System.currentTimeMillis()));
      }
      db.delete("parcels", "id=?", a(id));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void restore(Parcel p) {
    if (p == null
        || p.id <= 0
        || p.id > MAX_ROW_ID
        || p.createdAt < 0
        || p.code == null
        || !p.code.equals(code(p.code))) throw new IllegalArgumentException("无法恢复无效的取件记录");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, p.stationId);
      long historyId = -1;
      try (Cursor c =
          db.query(
              "history",
              new String[] {"id"},
              "parcel_id=? AND code=? AND station_id=? AND created_at=?",
              new String[] {
                Long.toString(p.id),
                p.code,
                Integer.toString(p.stationId),
                Long.toString(p.createdAt)
              },
              null,
              null,
              "collected_at DESC,id DESC",
              "1")) {
        if (c.moveToFirst()) historyId = c.getLong(0);
      }
      ContentValues v = pv(p.code, p.stationId, p.createdAt);
      v.put("id", p.id);
      db.insertOrThrow("parcels", null, v);
      if (historyId >= 0) db.delete("history", "id=?", a(historyId));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public void clearHistory() {
    getWritableDatabase().delete("history", null, null);
  }

  public List<HistoryEntry> history() {
    SQLiteDatabase db = getWritableDatabase();
    prune(db);
    List<HistoryEntry> out = new ArrayList<>();
    try (Cursor c =
        db.query(
            "history",
            new String[] {
              "id", "parcel_id", "code", "station_id", "station_name", "created_at", "collected_at"
            },
            null,
            null,
            null,
            null,
            "collected_at DESC,id DESC")) {
      while (c.moveToNext())
        out.add(
            new HistoryEntry(
                c.getLong(0),
                c.getLong(1),
                c.getString(2),
                c.getInt(3),
                c.getString(4),
                c.getLong(5),
                c.getLong(6)));
    }
    return out;
  }

  public void restoreHistory(long hid, int sid) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      station(db, sid);
      try (Cursor c =
          db.query(
              "history", new String[] {"code", "created_at"}, "id=?", a(hid), null, null, null)) {
        if (!c.moveToFirst()) throw new IllegalArgumentException("该历史记录已不存在");
        db.insertOrThrow("parcels", null, pv(c.getString(0), sid, c.getLong(1)));
      }
      db.delete("history", "id=?", a(hid));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyWidget();
  }

  public int reminderDays() {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT value FROM settings WHERE key='reminder_days'", null)) {
      return c.moveToFirst() ? Integer.parseInt(c.getString(0)) : 3;
    }
  }

  public void setReminderDays(int d) {
    if (d < 1 || d > 30) throw new IllegalArgumentException("提醒天数需为 1–30 天");
    setting(getWritableDatabase(), "reminder_days", Integer.toString(d));
  }

  private static void setting(SQLiteDatabase db, String k, String val) {
    ContentValues v = new ContentValues();
    v.put("key", k);
    v.put("value", val);
    db.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  private static void prune(SQLiteDatabase db) {
    db.delete("history", "collected_at < ?", a(System.currentTimeMillis() - RETENTION));
  }

  private static int nextOrder(SQLiteDatabase db) {
    try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM stations", null)) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  private static void reindex(SQLiteDatabase db) {
    List<Integer> ids = new ArrayList<>();
    try (Cursor c = db.rawQuery("SELECT id FROM stations ORDER BY sort_order", null)) {
      while (c.moveToNext()) ids.add(c.getInt(0));
    }
    db.execSQL("UPDATE stations SET sort_order=-sort_order-1");
    for (int i = 0; i < ids.size(); i++) {
      ContentValues v = new ContentValues();
      v.put("sort_order", i);
      db.update("stations", v, "id=?", a(ids.get(i)));
    }
  }

  private static ContentValues sv(String n, String f, String icon) {
    ContentValues v = new ContentValues();
    v.put("name", n);
    v.put("formats", f);
    v.put("icon_key", icon);
    return v;
  }

  private static ContentValues pv(String c, int s, long at) {
    ContentValues v = new ContentValues();
    v.put("code", c);
    v.put("station_id", s);
    v.put("created_at", at);
    return v;
  }

  private static ContentValues hv(long pid, String c, int sid, String n, long at, long got) {
    ContentValues v = new ContentValues();
    v.put("parcel_id", pid);
    v.put("code", c);
    v.put("station_id", sid);
    v.put("station_name", n);
    v.put("created_at", at);
    v.put("collected_at", got);
    return v;
  }

  private static String[] a(long value) {
    return new String[] {Long.toString(value)};
  }
}
