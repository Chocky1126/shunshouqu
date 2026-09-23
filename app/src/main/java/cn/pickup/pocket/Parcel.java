package cn.pickup.pocket;

public final class Parcel {
  public final long id;
  public final String code;
  public final int stationId;
  public final long createdAt;

  public Parcel(long id, String code, int stationId, long createdAt) {
    this.id = id;
    this.code = code;
    this.stationId = stationId;
    this.createdAt = createdAt;
  }
}
