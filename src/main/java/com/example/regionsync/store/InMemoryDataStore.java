package com.example.regionsync.store;

import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.model.SyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 线程安全的内存数据存储。
 * 维护数据项和同步事件日志。
 * 生产环境中应替换为真实数据库 + Kafka 等。
 */
@Component
public class InMemoryDataStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDataStore.class);

    /** 数据存储: key -> DataItem */
    private final ConcurrentHashMap<String, DataItem> dataStore = new ConcurrentHashMap<>();

    /** 同步事件日志（仅记录需要同步的事件，FINANCE 不记录） */
    private final CopyOnWriteArrayList<SyncEvent> eventLog = new CopyOnWriteArrayList<>();

    /** 事件序列号 */
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    /**
     * 存储数据项并记录同步事件。
     * FINANCE 类数据不记录事件（不跨区同步）。
     */
    public SyncEvent put(DataItem item, SyncEvent.EventType eventType) {
        dataStore.put(item.getKey(), item);

        // FINANCE 数据不同步 — 合规要求
        if (item.getCategory() == DataCategory.FINANCE) {
            log.debug("Stored FINANCE item locally (no sync): key={}", item.getKey());
            return null;
        }

        long seq = sequenceCounter.incrementAndGet();
        SyncEvent event = new SyncEvent(eventType, item, item.getSourceRegion(), seq);
        eventLog.add(event);
        log.debug("Stored item: key={}, category={}, version={}, seq={}",
                item.getKey(), item.getCategory(), item.getVersion(), seq);
        return event;
    }

    /** 按 key 获取（排除已删除） */
    public Optional<DataItem> get(String key) {
        DataItem item = dataStore.get(key);
        if (item != null && !item.isDeleted()) {
            return Optional.of(item);
        }
        return Optional.empty();
    }

    /** 按 key 获取（含已删除） */
    public Optional<DataItem> getIncludingDeleted(String key) {
        return Optional.ofNullable(dataStore.get(key));
    }

    /** 获取所有未删除数据 */
    public List<DataItem> getAll() {
        return dataStore.values().stream()
                .filter(item -> !item.isDeleted())
                .collect(Collectors.toList());
    }

    /** 按 category 获取所有未删除数据 */
    public List<DataItem> getAllByCategory(DataCategory category) {
        return dataStore.values().stream()
                .filter(item -> !item.isDeleted() && item.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * 应用从其他 Region 收到的同步事件。
     * 版本号高者胜；版本号相同时时间戳新者胜。
     */
    public boolean applySyncEvent(SyncEvent event) {
        DataItem incoming = event.getData();
        DataItem existing = dataStore.get(incoming.getKey());

        if (existing == null || incoming.getVersion() > existing.getVersion()) {
            dataStore.put(incoming.getKey(), incoming);
            log.debug("Applied sync event: type={}, key={}, category={}, version={}",
                    event.getType(), incoming.getKey(), incoming.getCategory(), incoming.getVersion());
            return true;
        } else if (incoming.getVersion() == existing.getVersion()
                && incoming.getTimestamp() > existing.getTimestamp()) {
            dataStore.put(incoming.getKey(), incoming);
            log.debug("Applied sync event (LWW): type={}, key={}, version={}",
                    event.getType(), incoming.getKey(), incoming.getVersion());
            return true;
        }

        log.debug("Skipped sync event (stale): key={}, incoming_v={}, existing_v={}",
                incoming.getKey(), incoming.getVersion(), existing.getVersion());
        return false;
    }

    /** 获取指定序列号之后的事件 */
    public List<SyncEvent> getEventsAfter(long afterSequence) {
        return eventLog.stream()
                .filter(event -> event.getSequenceNumber() > afterSequence)
                .collect(Collectors.toList());
    }

    /** 当前序列号 */
    public long getCurrentSequence() {
        return sequenceCounter.get();
    }

    /** 未删除数据总数 */
    public long getItemCount() {
        return dataStore.values().stream()
                .filter(item -> !item.isDeleted())
                .count();
    }

    /** 清空所有数据（测试用） */
    public void clear() {
        dataStore.clear();
        eventLog.clear();
        sequenceCounter.set(0);
    }
}
