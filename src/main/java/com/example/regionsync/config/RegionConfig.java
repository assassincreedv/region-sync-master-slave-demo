package com.example.regionsync.config;

import com.example.regionsync.model.DataCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Region 节点配置。
 *
 * 核心思想：每个 Region 对不同 DataCategory 可能是 Master 或 Slave。
 * <ul>
 *   <li>ownership: 声明本节点对哪些 DataCategory 拥有 Master 权限</li>
 *   <li>categoryOwnerMap: 全局映射，每种 DataCategory 的 Master Region ID</li>
 *   <li>peerUrls: 所有其他 Region 节点的 URL (regionId -> url)</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "region")
public class RegionConfig {

    /** 本节点的 Region ID，例如 NA / EU / CN */
    private String id = "NA";

    /** 本节点拥有 Master 权限的 DataCategory 列表 */
    private List<DataCategory> ownership = new ArrayList<>();

    /**
     * 全局数据归属映射: DataCategory -> Master Region ID。
     * 对于按 Region 归属的类型(JOB/TALENT/FINANCE)，此处用 "REGIONAL" 表示
     * 由各 Region 自己做 Master（即 ownerRegion = 数据创建时的 regionId）。
     */
    private Map<DataCategory, String> categoryOwnerMap = new EnumMap<>(DataCategory.class);

    /** 其他 Region 节点: regionId -> URL */
    private Map<String, String> peerUrls = new LinkedHashMap<>();

    /** 同步拉取间隔（毫秒） */
    private long syncIntervalMs = 3000;

    /** 健康检查间隔（毫秒） */
    private long healthCheckIntervalMs = 5000;

    /**
     * 判断本节点是否是指定 DataCategory 的全局 Master。
     * 对于 REGIONAL 类型的数据（JOB/TALENT/FINANCE），总是返回 true，
     * 因为每个 Region 都是自己数据的 Master。
     */
    public boolean isMasterFor(DataCategory category) {
        String masterRegion = categoryOwnerMap.get(category);
        if ("REGIONAL".equalsIgnoreCase(masterRegion)) {
            return true; // regional 数据，每个 region 都是自己数据的 master
        }
        return id.equalsIgnoreCase(masterRegion);
    }

    /**
     * 判断某条具体数据是否属于本节点（考虑 REGIONAL 类型）。
     * 对于 REGIONAL 数据，需要检查 data 的 ownerRegion 是否等于本节点 id。
     */
    public boolean isOwnerOf(DataCategory category, String dataOwnerRegion) {
        String masterRegion = categoryOwnerMap.get(category);
        if ("REGIONAL".equalsIgnoreCase(masterRegion)) {
            return id.equalsIgnoreCase(dataOwnerRegion);
        }
        return id.equalsIgnoreCase(masterRegion);
    }

    /**
     * 获取某个 DataCategory 的 Master Region ID。
     * 对于 REGIONAL 数据返回 "REGIONAL"。
     */
    public String getMasterRegionFor(DataCategory category) {
        return categoryOwnerMap.getOrDefault(category, id);
    }

    /**
     * 获取指定 region 的 URL。
     */
    public String getPeerUrl(String regionId) {
        return peerUrls.get(regionId);
    }

    // ── Getters & Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<DataCategory> getOwnership() { return ownership; }
    public void setOwnership(List<DataCategory> ownership) { this.ownership = ownership; }

    public Map<DataCategory, String> getCategoryOwnerMap() { return categoryOwnerMap; }
    public void setCategoryOwnerMap(Map<DataCategory, String> categoryOwnerMap) {
        this.categoryOwnerMap = categoryOwnerMap;
    }

    public Map<String, String> getPeerUrls() { return peerUrls; }
    public void setPeerUrls(Map<String, String> peerUrls) { this.peerUrls = peerUrls; }

    public long getSyncIntervalMs() { return syncIntervalMs; }
    public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }

    public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }

    @Override
    public String toString() {
        return "RegionConfig{" +
                "id='" + id + '\'' +
                ", ownership=" + ownership +
                ", categoryOwnerMap=" + categoryOwnerMap +
                ", peerUrls=" + peerUrls +
                '}';
    }
}
