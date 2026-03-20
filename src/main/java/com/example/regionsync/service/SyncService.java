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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同步服务 — 负责 Region 之间的数据同步。
 *
 * <p>同步模型（不再是单一 Master-Slave，而是 Peer-to-Peer）：
 * <ol>
 *   <li>Push: 当 owner region 写入数据后，立即推送 SyncEvent 给所有 peer</li>
 *   <li>Pull: 每个 region 定期从所有 peer 拉取遗漏的事件（容灾兜底）</li>
 * </ol>
 *
 * <p>FINANCE 类数据不生成 SyncEvent，因此不会被同步。
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final InMemoryDataStore dataStore;
    private final RegionConfig regionConfig;
    private final RestTemplate restTemplate;

    /** 记录从每个 peer 已同步到的最新序列号 */
    private final Map<String, AtomicLong> peerSyncSequences = new ConcurrentHashMap<>();

    public SyncService(InMemoryDataStore dataStore, RegionConfig regionConfig, RestTemplate restTemplate) {
        this.dataStore = dataStore;
        this.regionConfig = regionConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * 将同步事件推送给所有 peer（写操作完成后调用）。
     */
    public void pushEventToPeers(SyncEvent event) {
        Map<String, String> peerUrls = regionConfig.getPeerUrls();
        if (peerUrls == null || peerUrls.isEmpty()) {
            log.debug("No peer URLs configured, skipping push");
            return;
        }

        for (Map.Entry<String, String> entry : peerUrls.entrySet()) {
            String peerId = entry.getKey();
            String peerUrl = entry.getValue();
            try {
                String url = normalizeUrl(peerUrl) + "/api/sync/receive";
                restTemplate.postForObject(url, event, Void.class);
                log.debug("Pushed event to peer [{}]: seq={}, category={}",
                        peerId, event.getSequenceNumber(), event.getDataCategory());
            } catch (Exception e) {
                log.warn("Failed to push event to peer [{}]: {}", peerId, e.getMessage());
            }
        }
    }

    /**
     * 接收来自其他 Region 的同步事件（被动接收）。
     */
    public boolean receiveEvent(SyncEvent event) {
        log.debug("Received sync event from [{}]: type={}, key={}, category={}, seq={}",
                event.getSourceRegion(), event.getType(),
                event.getData().getKey(), event.getDataCategory(), event.getSequenceNumber());
        return dataStore.applySyncEvent(event);
    }

    /**
     * 定期从所有 peer 拉取遗漏的事件（兜底机制）。
     */
    @Scheduled(fixedDelayString = "${region.sync-interval-ms:3000}")
    public void pullFromPeers() {
        Map<String, String> peerUrls = regionConfig.getPeerUrls();
        if (peerUrls == null || peerUrls.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : peerUrls.entrySet()) {
            String peerId = entry.getKey();
            String peerUrl = entry.getValue();
            pullFromPeer(peerId, peerUrl);
        }
    }

    private void pullFromPeer(String peerId, String peerUrl) {
        AtomicLong lastSeq = peerSyncSequences.computeIfAbsent(peerId, k -> new AtomicLong(0));

        try {
            String url = normalizeUrl(peerUrl) +
                    "/api/sync/events?afterSequence=" + lastSeq.get();

            SyncEvent[] events = restTemplate.getForObject(url, SyncEvent[].class);

            if (events != null && events.length > 0) {
                log.info("Pulled {} events from peer [{}] (after seq={})",
                        events.length, peerId, lastSeq.get());
                long maxSeq = lastSeq.get();
                for (SyncEvent event : events) {
                    dataStore.applySyncEvent(event);
                    maxSeq = Math.max(maxSeq, event.getSequenceNumber());
                }
                long finalMaxSeq = maxSeq;
                lastSeq.updateAndGet(current -> Math.max(current, finalMaxSeq));
            }
        } catch (Exception e) {
            log.warn("Failed to pull events from peer [{}] at {}: {}", peerId, peerUrl, e.getMessage());
        }
    }

    /** 获取本节点指定序列号之后的事件（供其他 peer 拉取） */
    public List<SyncEvent> getEventsAfter(long afterSequence) {
        return dataStore.getEventsAfter(afterSequence);
    }

    /** 当前序列号 */
    public long getCurrentSequence() {
        return dataStore.getCurrentSequence();
    }

    /** 获取各 peer 的同步进度 */
    public Map<String, Long> getPeerSyncProgress() {
        Map<String, Long> progress = new java.util.LinkedHashMap<>();
        peerSyncSequences.forEach((peerId, seq) -> progress.put(peerId, seq.get()));
        return progress;
    }

    private static String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
