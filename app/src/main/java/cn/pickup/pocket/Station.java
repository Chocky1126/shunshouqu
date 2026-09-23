package cn.pickup.pocket;

public final class Station {
  public final int id;
  public final String name;
  public final String formats;
  public final String iconKey;

  public Station(int id, String name, String formats) {
    this(id, name, formats, StationIcons.defaultForId(id));
  }

  public Station(int id, String name, String formats, String iconKey) {
    this.id = id;
    this.name = name;
    this.formats = formats;
    this.iconKey = StationIcons.checked(iconKey);
  }
}
