package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.RegionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for health checking and failover management.
 *
 * For SLAVE nodes:
 * - Periodically checks if the MASTER is healthy
 * - After maxHealthCheckFailures consecutive failures, triggers failover
 * - On failover, the slave promotes itself to MASTER
 *
 * For MASTER nodes:
 * - Periodically checks all SLAVE nodes health
 * - Tracks which slaves are healthy/unhealthy
 */
@Service
public class FailoverService {

    private static final Logger log = LoggerFactory.getLogger(FailoverService.class);

    private final RegionConfig regionConfig;
    private final RestTemplate restTemplate;
    private final long startTime;

    /** Consecutive health check failure count for master (used by slaves) */
    private final AtomicInteger masterFailureCount = new AtomicInteger(0);

    /** Health status of slave nodes (used by master) */
    private final Map<String, Boolean> slaveHealthStatus = new ConcurrentHashMap<>();

    public FailoverService(RegionConfig regionConfig, RestTemplate restTemplate) {
        this.regionConfig = regionConfig;
        this.restTemplate = restTemplate;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Periodic health check.
     * Slaves check master health; Master checks slave health.
     */
    @Scheduled(fixedDelayString = "${region.health-check-interval-ms:5000}")
    public void performHealthCheck() {
        if (regionConfig.isSlave()) {
            checkMasterHealth();
        } else if (regionConfig.isMaster()) {
            checkSlaveHealth();
        }
    }

    /**
     * Check if the master node is healthy (called by slaves).
     */
    private void checkMasterHealth() {
        String masterUrl = regionConfig.getMasterUrl();
        if (masterUrl == null || masterUrl.isEmpty()) {
            return;
        }

        try {
            String url = normalizeUrl(masterUrl) + "/api/sync/status";
            RegionInfo masterInfo = restTemplate.getForObject(url, RegionInfo.class);

            if (masterInfo != null && masterInfo.isHealthy()) {
                int previousFailures = masterFailureCount.getAndSet(0);
                if (previousFailures > 0) {
                    log.info("Master {} is healthy again (was at {} failures)", masterUrl, previousFailures);
                }
            } else {
                handleMasterFailure(masterUrl);
            }
        } catch (Exception e) {
            handleMasterFailure(masterUrl);
            log.warn("Health check failed for master {}: {}", masterUrl, e.getMessage());
        }
    }

    private void handleMasterFailure(String masterUrl) {
        int failures = masterFailureCount.incrementAndGet();
        log.warn("Master {} health check failed ({}/{})",
                masterUrl, failures, regionConfig.getMaxHealthCheckFailures());

        if (failures >= regionConfig.getMaxHealthCheckFailures()) {
            triggerFailover();
        }
    }

    /**
     * Trigger failover: promote this slave to master.
     */
    private void triggerFailover() {
        log.warn("=== FAILOVER TRIGGERED ===");
        log.warn("Promoting slave [{}] to MASTER role", regionConfig.getId());

        regionConfig.setRole(RegionConfig.Role.MASTER);
        regionConfig.setMasterUrl("");
        masterFailureCount.set(0);

        log.warn("Node [{}] is now MASTER", regionConfig.getId());
        log.warn("=== FAILOVER COMPLETE ===");
    }

    /**
     * Check health of all slave nodes (called by master).
     */
    private void checkSlaveHealth() {
        List<String> slaveUrls = regionConfig.getSlaveUrls();
        if (slaveUrls == null || slaveUrls.isEmpty()) {
            return;
        }

        for (String slaveUrl : slaveUrls) {
            try {
                String url = normalizeUrl(slaveUrl) + "/api/sync/status";
                RegionInfo slaveInfo = restTemplate.getForObject(url, RegionInfo.class);

                boolean healthy = slaveInfo != null && slaveInfo.isHealthy();
                Boolean previousStatus = slaveHealthStatus.put(slaveUrl, healthy);

                if (previousStatus != null && previousStatus && !healthy) {
                    log.warn("Slave {} became unhealthy", slaveUrl);
                } else if (previousStatus != null && !previousStatus && healthy) {
                    log.info("Slave {} recovered", slaveUrl);
                }
            } catch (Exception e) {
                Boolean previousStatus = slaveHealthStatus.put(slaveUrl, false);
                if (previousStatus == null || previousStatus) {
                    log.warn("Slave {} is unreachable: {}", slaveUrl, e.getMessage());
                }
            }
        }
    }

    /**
     * Get the health status of slave nodes (master only).
     */
    public Map<String, Boolean> getSlaveHealthStatus() {
        return Map.copyOf(slaveHealthStatus);
    }

    /**
     * Get node uptime in milliseconds.
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get the master failure count (slave only).
     */
    public int getMasterFailureCount() {
        return masterFailureCount.get();
    }

    private static String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
