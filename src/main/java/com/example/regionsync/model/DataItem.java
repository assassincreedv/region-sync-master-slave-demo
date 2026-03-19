package com.example.regionsync.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a data item stored in the region's data store.
 * Each item is versioned to support conflict resolution during sync.
 */
public class DataItem {

    private String id;
    private String key;
    private String value;
    private long version;
    private long timestamp;
    private String sourceRegion;
    private boolean deleted;

    public DataItem() {
        this.id = UUID.randomUUID().toString();
        this.version = 1;
        this.timestamp = System.currentTimeMillis();
        this.deleted = false;
    }

    public DataItem(String key, String value, String sourceRegion) {
        this();
        this.key = key;
        this.value = value;
        this.sourceRegion = sourceRegion;
    }

    /**
     * Create a copy of this item with incremented version.
     */
    public DataItem withUpdatedValue(String newValue, String region) {
        DataItem updated = new DataItem();
        updated.id = this.id;
        updated.key = this.key;
        updated.value = newValue;
        updated.version = this.version + 1;
        updated.timestamp = System.currentTimeMillis();
        updated.sourceRegion = region;
        updated.deleted = false;
        return updated;
    }

    /**
     * Create a soft-deleted copy of this item.
     */
    public DataItem asDeleted(String region) {
        DataItem deleted = new DataItem();
        deleted.id = this.id;
        deleted.key = this.key;
        deleted.value = this.value;
        deleted.version = this.version + 1;
        deleted.timestamp = System.currentTimeMillis();
        deleted.sourceRegion = region;
        deleted.deleted = true;
        return deleted;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(String sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataItem dataItem = (DataItem) o;
        return Objects.equals(id, dataItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DataItem{" +
                "id='" + id + '\'' +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", version=" + version +
                ", sourceRegion='" + sourceRegion + '\'' +
                ", deleted=" + deleted +
                '}';
    }
}
