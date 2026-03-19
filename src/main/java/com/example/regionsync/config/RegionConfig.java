package com.example.regionsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the region node.
 * Defines the node's identity, role, and sync parameters.
 */
@Configuration
@ConfigurationProperties(prefix = "region")
public class RegionConfig {

    private String id = "region-1";
    private Role role = Role.MASTER;
    private String masterUrl = "";
    private List<String> slaveUrls = new ArrayList<>();
    private long syncIntervalMs = 3000;
    private long healthCheckIntervalMs = 5000;
    private long healthCheckTimeoutMs = 2000;
    private int maxHealthCheckFailures = 3;

    public enum Role {
        MASTER, SLAVE
    }

    public boolean isMaster() {
        return role == Role.MASTER;
    }

    public boolean isSlave() {
        return role == Role.SLAVE;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public List<String> getSlaveUrls() {
        return slaveUrls;
    }

    public void setSlaveUrls(List<String> slaveUrls) {
        this.slaveUrls = slaveUrls;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public long getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }

    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }

    public long getHealthCheckTimeoutMs() {
        return healthCheckTimeoutMs;
    }

    public void setHealthCheckTimeoutMs(long healthCheckTimeoutMs) {
        this.healthCheckTimeoutMs = healthCheckTimeoutMs;
    }

    public int getMaxHealthCheckFailures() {
        return maxHealthCheckFailures;
    }

    public void setMaxHealthCheckFailures(int maxHealthCheckFailures) {
        this.maxHealthCheckFailures = maxHealthCheckFailures;
    }

    @Override
    public String toString() {
        return "RegionConfig{" +
                "id='" + id + '\'' +
                ", role=" + role +
                ", masterUrl='" + masterUrl + '\'' +
                ", slaveUrls=" + slaveUrls +
                ", syncIntervalMs=" + syncIntervalMs +
                '}';
    }
}
