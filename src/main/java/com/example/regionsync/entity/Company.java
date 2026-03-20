package com.example.regionsync.entity;

import com.example.regionsync.model.DataCategory;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Company 实体 — 模拟真实的业务数据。
 * 每条 Company 数据归属于一个 {@link DataCategory} 和一个 ownerRegion，
 * 只有 ownerRegion 的节点才拥有该条数据的写权限。
 */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @Column(length = 64)
    private String id;

    /** 业务唯一键（如 "eu-job-001"），用于跨区同步时识别同一条数据 */
    @Column(name = "biz_key", nullable = false, unique = true, length = 128)
    private String bizKey;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 512)
    private String address;

    @Column(length = 128)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataCategory category;

    /** 该条数据归属的 Region（即该数据的 Master Region） */
    @Column(name = "owner_region", nullable = false, length = 16)
    private String ownerRegion;

    /** 最后一次修改该数据的 Region */
    @Column(name = "source_region", length = 16)
    private String sourceRegion;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    public Company() {
        this.id = UUID.randomUUID().toString();
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    public Company(String bizKey, String name, String address, String industry,
                   DataCategory category, String ownerRegion) {
        this();
        this.bizKey = bizKey;
        this.name = name;
        this.address = address;
        this.industry = industry;
        this.category = category;
        this.ownerRegion = ownerRegion;
        this.sourceRegion = ownerRegion;
    }

    // ── Getters & Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public DataCategory getCategory() { return category; }
    public void setCategory(DataCategory category) { this.category = category; }

    public String getOwnerRegion() { return ownerRegion; }
    public void setOwnerRegion(String ownerRegion) { this.ownerRegion = ownerRegion; }

    public String getSourceRegion() { return sourceRegion; }
    public void setSourceRegion(String sourceRegion) { this.sourceRegion = sourceRegion; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Company company = (Company) o;
        return Objects.equals(id, company.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Company{" +
                "id='" + id + '\'' +
                ", bizKey='" + bizKey + '\'' +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", ownerRegion='" + ownerRegion + '\'' +
                ", version=" + version +
                ", deleted=" + deleted +
                '}';
    }
}
