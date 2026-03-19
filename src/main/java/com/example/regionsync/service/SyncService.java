package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.store.InMemoryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for data synchronization between master and slave regions.
 *
 * Two sync mechanisms:
 * 1. Push-based (Master -> Slave): Master pushes events to slaves after each write.
 * 2. Pull-based (Slave -> Master): Slave periodically polls master for new events
 *    (as a fallback in case push fails).
 *
 * This dual approach ensures:
 * - Low latency sync via push
 * - Reliability via pull (catches missed pushes)
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final InMemoryDataStore dataStore;
    private final RegionConfig regionConfig;
    private final RestTemplate restTemplate;

    /** Tracks the last sequence number synced from master (used by slaves) */
    private final AtomicLong lastSyncedSequence = new AtomicLong(0);

    public SyncService(InMemoryDataStore dataStore, RegionConfig regionConfig, RestTemplate restTemplate) {
        this.dataStore = dataStore;
        this.regionConfig = regionConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * Push a sync event to all slave regions (called by master after write operations).
     * Failures are logged but don't block the master's write operation.
     */
    public void pushEventToSlaves(SyncEvent event) {
        if (!regionConfig.isMaster()) {
            return;
        }

        List<String> slaveUrls = regionConfig.getSlaveUrls();
        if (slaveUrls == null || slaveUrls.isEmpty()) {
            log.debug("No slave URLs configured, skipping push");
            return;
        }

        for (String slaveUrl : slaveUrls) {
            try {
                String url = slaveUrl.replaceAll("/$", "") + "/api/sync/receive";
                restTemplate.postForObject(url, event, Void.class);
                log.debug("Pushed event to slave {}: seq={}", slaveUrl, event.getSequenceNumber());
            } catch (Exception e) {
                log.warn("Failed to push event to slave {}: {}", slaveUrl, e.getMessage());
            }
        }
    }

    /**
     * Receive a sync event from master (called on slave nodes).
     * Applies the event to the local data store using version-based conflict resolution.
     */
    public boolean receiveEvent(SyncEvent event) {
        log.debug("Received sync event: type={}, key={}, seq={}",
                event.getType(), event.getData().getKey(), event.getSequenceNumber());
        boolean applied = dataStore.applySyncEvent(event);
        if (applied) {
            updateLastSyncedSequence(event.getSequenceNumber());
        }
        return applied;
    }

    /**
     * Periodic pull from master (slave only).
     * Fetches all events after the last synced sequence number.
     * This serves as a fallback mechanism to catch any events that were missed during push.
     */
    @Scheduled(fixedDelayString = "${region.sync-interval-ms:3000}")
    public void pullFromMaster() {
        if (!regionConfig.isSlave()) {
            return;
        }

        String masterUrl = regionConfig.getMasterUrl();
        if (masterUrl == null || masterUrl.isEmpty()) {
            log.warn("Master URL not configured for slave node");
            return;
        }

        try {
            String url = masterUrl.replaceAll("/$", "") +
                    "/api/sync/events?afterSequence=" + lastSyncedSequence.get();

            @SuppressWarnings("unchecked")
            SyncEvent[] events = restTemplate.getForObject(url, SyncEvent[].class);

            if (events != null && events.length > 0) {
                log.info("Pulled {} events from master (after seq={})", events.length, lastSyncedSequence.get());
                for (SyncEvent event : events) {
                    dataStore.applySyncEvent(event);
                    updateLastSyncedSequence(event.getSequenceNumber());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to pull events from master {}: {}", masterUrl, e.getMessage());
        }
    }

    /**
     * Get events after a given sequence number (called by slaves via API).
     */
    public List<SyncEvent> getEventsAfter(long afterSequence) {
        return dataStore.getEventsAfter(afterSequence);
    }

    /**
     * Get the current sequence number.
     */
    public long getCurrentSequence() {
        return dataStore.getCurrentSequence();
    }

    /**
     * Get the last synced sequence number (for slave nodes).
     */
    public long getLastSyncedSequence() {
        return lastSyncedSequence.get();
    }

    private void updateLastSyncedSequence(long sequence) {
        lastSyncedSequence.updateAndGet(current -> Math.max(current, sequence));
    }
}
