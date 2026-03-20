package com.example.regionsync.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 数据项模型。
 * 每条数据归属于一个 {@link DataCategory} 和一个 ownerRegion，
 * 只有 ownerRegion 才拥有该条数据的写权限。
 */
public class DataItem {

    private String id;
    private String key;
    private String value;
    private DataCategory category;
    /** 该条数据归属的 Region（即该数据的 Master Region） */
    private String ownerRegion;
    private long version;
    private long timestamp;
    /** 最后一次修改该数据的 Region */
    private String sourceRegion;
    private boolean deleted;

    public DataItem() {
        this.id = UUID.randomUUID().toString();
        this.version = 1;
        this.timestamp = System.currentTimeMillis();
        this.deleted = false;
    }

    public DataItem(String key, String value, DataCategory category, String ownerRegion) {
        this();
        this.key = key;
        this.value = value;
        this.category = category;
        this.ownerRegion = ownerRegion;
        this.sourceRegion = ownerRegion;
    }

    /** 创建一个新版本的副本（更新值）。 */
    public DataItem withUpdatedValue(String newValue, String region) {
        DataItem updated = new DataItem();
        updated.id = this.id;
        updated.key = this.key;
        updated.value = newValue;
        updated.category = this.category;
        updated.ownerRegion = this.ownerRegion;
        updated.version = this.version + 1;
        updated.timestamp = System.currentTimeMillis();
        updated.sourceRegion = region;
        updated.deleted = false;
        return updated;
    }

    /** 创建一个软删除的副本。 */
    public DataItem asDeleted(String region) {
        DataItem d = new DataItem();
        d.id = this.id;
        d.key = this.key;
        d.value = this.value;
        d.category = this.category;
        d.ownerRegion = this.ownerRegion;
        d.version = this.version + 1;
        d.timestamp = System.currentTimeMillis();
        d.sourceRegion = region;
        d.deleted = true;
        return d;
    }

    // ── Getters & Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public DataCategory getCategory() { return category; }
    public void setCategory(DataCategory category) { this.category = category; }

    public String getOwnerRegion() { return ownerRegion; }
    public void setOwnerRegion(String ownerRegion) { this.ownerRegion = ownerRegion; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSourceRegion() { return sourceRegion; }
    public void setSourceRegion(String sourceRegion) { this.sourceRegion = sourceRegion; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataItem that = (DataItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DataItem{" +
                "id='" + id + '\'' +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", category=" + category +
                ", ownerRegion='" + ownerRegion + '\'' +
                ", version=" + version +
                ", sourceRegion='" + sourceRegion + '\'' +
                ", deleted=" + deleted +
                '}';
    }
}
