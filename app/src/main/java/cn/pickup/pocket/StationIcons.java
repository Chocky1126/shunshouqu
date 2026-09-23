package cn.pickup.pocket;

/** Stable keys and resources for the built-in station icon set. */
final class StationIcons {
  static final String[] KEYS = {
    "shop", "gate", "locker", "home", "office", "school", "market", "parcel"
  };
  static final String[] LABELS = {
    "驿站", "门岗", "快递柜", "住宅", "公司", "学校", "超市", "包裹"
  };
  static final int[] RESOURCES = {
    R.drawable.pixel_shop,
    R.drawable.pixel_gate,
    R.drawable.pixel_locker,
    R.drawable.pixel_home,
    R.drawable.pixel_office,
    R.drawable.pixel_school,
    R.drawable.pixel_market,
    R.drawable.pixel_parcel
  };

  private StationIcons() {}

  static String checked(String key) {
    for (String value : KEYS) if (value.equals(key)) return key;
    throw new IllegalArgumentException("请选择有效的站点图标");
  }

  static int indexOf(String key) {
    for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
    return 0;
  }

  static String defaultForId(int id) {
    return KEYS[Math.floorMod(id, 3)];
  }
}
