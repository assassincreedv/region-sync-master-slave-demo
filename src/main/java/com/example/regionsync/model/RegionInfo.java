package com.example.regionsync.model;

/**
 * Represents the status information of a region node.
 * Used for health checks and status monitoring.
 */
public class RegionInfo {

    private String regionId;
    private String role;
    private long itemCount;
    private long lastSyncSequence;
    private long uptime;
    private boolean healthy;

    public RegionInfo() {
    }

    public RegionInfo(String regionId, String role, long itemCount, long lastSyncSequence, long uptime, boolean healthy) {
        this.regionId = regionId;
        this.role = role;
        this.itemCount = itemCount;
        this.lastSyncSequence = lastSyncSequence;
        this.uptime = uptime;
        this.healthy = healthy;
    }

    // Getters and Setters

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getItemCount() {
        return itemCount;
    }

    public void setItemCount(long itemCount) {
        this.itemCount = itemCount;
    }

    public long getLastSyncSequence() {
        return lastSyncSequence;
    }

    public void setLastSyncSequence(long lastSyncSequence) {
        this.lastSyncSequence = lastSyncSequence;
    }

    public long getUptime() {
        return uptime;
    }

    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public String toString() {
        return "RegionInfo{" +
                "regionId='" + regionId + '\'' +
                ", role='" + role + '\'' +
                ", itemCount=" + itemCount +
                ", lastSyncSequence=" + lastSyncSequence +
                ", healthy=" + healthy +
                '}';
    }
}
