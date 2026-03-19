package com.example.regionsync.controller;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.RegionInfo;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.service.FailoverService;
import com.example.regionsync.service.SyncService;
import com.example.regionsync.store.InMemoryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal REST controller for synchronization between region nodes.
 *
 * Provides endpoints for:
 * - Receiving sync events (slave receives from master push)
 * - Fetching sync events (slave pulls from master)
 * - Status/health information
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final FailoverService failoverService;
    private final RegionConfig regionConfig;
    private final InMemoryDataStore dataStore;

    public SyncController(SyncService syncService, FailoverService failoverService,
                          RegionConfig regionConfig, InMemoryDataStore dataStore) {
        this.syncService = syncService;
        this.failoverService = failoverService;
        this.regionConfig = regionConfig;
        this.dataStore = dataStore;
    }

    /**
     * Receive a sync event from master (push-based sync).
     * Called by master to push events to this slave node.
     *
     * POST /api/sync/receive
     */
    @PostMapping("/receive")
    public ResponseEntity<?> receiveSyncEvent(@RequestBody SyncEvent event) {
        boolean applied = syncService.receiveEvent(event);
        return ResponseEntity.ok(Map.of(
                "applied", applied,
                "regionId", regionConfig.getId()
        ));
    }

    /**
     * Get sync events after a given sequence number (pull-based sync).
     * Called by slaves to fetch new events from master.
     *
     * GET /api/sync/events?afterSequence=0
     */
    @GetMapping("/events")
    public ResponseEntity<List<SyncEvent>> getEvents(
            @RequestParam(defaultValue = "0") long afterSequence) {
        List<SyncEvent> events = syncService.getEventsAfter(afterSequence);
        return ResponseEntity.ok(events);
    }

    /**
     * Get the current status/health of this region node.
     *
     * GET /api/sync/status
     */
    @GetMapping("/status")
    public ResponseEntity<RegionInfo> getStatus() {
        RegionInfo info = new RegionInfo(
                regionConfig.getId(),
                regionConfig.getRole().name(),
                dataStore.getItemCount(),
                syncService.getLastSyncedSequence(),
                failoverService.getUptime(),
                true
        );
        return ResponseEntity.ok(info);
    }

    /**
     * Get detailed cluster status including slave health (master only).
     *
     * GET /api/sync/cluster-status
     */
    @GetMapping("/cluster-status")
    public ResponseEntity<?> getClusterStatus() {
        RegionInfo nodeInfo = new RegionInfo(
                regionConfig.getId(),
                regionConfig.getRole().name(),
                dataStore.getItemCount(),
                syncService.getCurrentSequence(),
                failoverService.getUptime(),
                true
        );

        return ResponseEntity.ok(Map.of(
                "node", nodeInfo,
                "slaveHealth", failoverService.getSlaveHealthStatus(),
                "currentSequence", syncService.getCurrentSequence()
        ));
    }
}
