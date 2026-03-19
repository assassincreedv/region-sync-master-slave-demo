package com.example.regionsync.model;

import java.util.Map;

/**
 * Region 节点状态信息，用于健康检查和状态监控。
 */
public class RegionInfo {

    private String regionId;
    private long itemCount;
    private long lastSyncSequence;
    private long uptime;
    private boolean healthy;
    /** 该 region 拥有 Master 权限的数据类别列表 */
    private Map<String, String> ownership;

    public RegionInfo() {}

    public RegionInfo(String regionId, long itemCount, long lastSyncSequence,
                      long uptime, boolean healthy, Map<String, String> ownership) {
        this.regionId = regionId;
        this.itemCount = itemCount;
        this.lastSyncSequence = lastSyncSequence;
        this.uptime = uptime;
        this.healthy = healthy;
        this.ownership = ownership;
    }

    // ── Getters & Setters ──

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public long getItemCount() { return itemCount; }
    public void setItemCount(long itemCount) { this.itemCount = itemCount; }

    public long getLastSyncSequence() { return lastSyncSequence; }
    public void setLastSyncSequence(long lastSyncSequence) { this.lastSyncSequence = lastSyncSequence; }

    public long getUptime() { return uptime; }
    public void setUptime(long uptime) { this.uptime = uptime; }

    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public Map<String, String> getOwnership() { return ownership; }
    public void setOwnership(Map<String, String> ownership) { this.ownership = ownership; }

    @Override
    public String toString() {
        return "RegionInfo{" +
                "regionId='" + regionId + '\'' +
                ", itemCount=" + itemCount +
                ", lastSyncSequence=" + lastSyncSequence +
                ", healthy=" + healthy +
                ", ownership=" + ownership +
                '}';
    }
}
