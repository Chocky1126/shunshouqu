package cn.pickup.pocket;

public final class HistoryEntry {
  public final long id, parcelId, createdAt, collectedAt;
  public final String code, stationName;
  public final int stationId;

  public HistoryEntry(
      long id,
      long parcelId,
      String code,
      int stationId,
      String stationName,
      long createdAt,
      long collectedAt) {
    this.id = id;
    this.parcelId = parcelId;
    this.code = code;
    this.stationId = stationId;
    this.stationName = stationName;
    this.createdAt = createdAt;
    this.collectedAt = collectedAt;
  }
}
