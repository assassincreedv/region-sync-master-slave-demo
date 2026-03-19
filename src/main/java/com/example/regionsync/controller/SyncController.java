package com.example.regionsync.controller;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.RegionInfo;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.service.SyncService;
import com.example.regionsync.store.InMemoryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步 REST 控制器 — 用于 Region 之间的数据同步。
 *
 * <p>提供：
 * <ul>
 *   <li>接收同步事件（push）</li>
 *   <li>拉取同步事件（pull）</li>
 *   <li>节点状态查询</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final RegionConfig regionConfig;
    private final InMemoryDataStore dataStore;

    private final long startTime = System.currentTimeMillis();

    public SyncController(SyncService syncService, RegionConfig regionConfig,
                          InMemoryDataStore dataStore) {
        this.syncService = syncService;
        this.regionConfig = regionConfig;
        this.dataStore = dataStore;
    }

    /**
     * 接收来自其他 Region 推送的同步事件。
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
     * 返回指定序列号之后的同步事件（供其他 Region 拉取）。
     * GET /api/sync/events?afterSequence=0
     */
    @GetMapping("/events")
    public ResponseEntity<List<SyncEvent>> getEvents(
            @RequestParam(defaultValue = "0") long afterSequence) {
        return ResponseEntity.ok(syncService.getEventsAfter(afterSequence));
    }

    /**
     * 节点状态/健康检查。
     * GET /api/sync/status
     */
    @GetMapping("/status")
    public ResponseEntity<RegionInfo> getStatus() {
        // 构造 ownership 信息
        Map<String, String> ownership = new LinkedHashMap<>();
        for (DataCategory cat : DataCategory.values()) {
            String masterRegion = regionConfig.getMasterRegionFor(cat);
            boolean isLocal = regionConfig.isMasterFor(cat);
            ownership.put(cat.name(), masterRegion + (isLocal ? " (LOCAL MASTER)" : " (SLAVE)"));
        }

        RegionInfo info = new RegionInfo(
                regionConfig.getId(),
                dataStore.getItemCount(),
                syncService.getCurrentSequence(),
                System.currentTimeMillis() - startTime,
                true,
                ownership
        );
        return ResponseEntity.ok(info);
    }

    /**
     * 集群状态：本节点信息 + peer 同步进度。
     * GET /api/sync/cluster-status
     */
    @GetMapping("/cluster-status")
    public ResponseEntity<?> getClusterStatus() {
        Map<String, String> ownership = new LinkedHashMap<>();
        for (DataCategory cat : DataCategory.values()) {
            ownership.put(cat.name(), regionConfig.getMasterRegionFor(cat));
        }

        return ResponseEntity.ok(Map.of(
                "regionId", regionConfig.getId(),
                "itemCount", dataStore.getItemCount(),
                "currentSequence", syncService.getCurrentSequence(),
                "ownership", ownership,
                "peerSyncProgress", syncService.getPeerSyncProgress(),
                "peers", regionConfig.getPeerUrls()
        ));
    }
}
