package com.example.regionsync.store;

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
 * Thread-safe in-memory data store that maintains both the data items
 * and the event log for synchronization.
 *
 * In a production system, this would be replaced by a real database
 * and a persistent event log (e.g., Kafka, database-backed event store).
 */
@Component
public class InMemoryDataStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDataStore.class);

    /** Primary data storage: key -> DataItem */
    private final ConcurrentHashMap<String, DataItem> dataStore = new ConcurrentHashMap<>();

    /** Event log for sync replication, ordered by sequence number */
    private final CopyOnWriteArrayList<SyncEvent> eventLog = new CopyOnWriteArrayList<>();

    /** Monotonically increasing sequence number for event ordering */
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    /**
     * Store a data item and record the sync event.
     */
    public SyncEvent put(DataItem item, SyncEvent.EventType eventType) {
        dataStore.put(item.getKey(), item);
        long seq = sequenceCounter.incrementAndGet();
        SyncEvent event = new SyncEvent(eventType, item, item.getSourceRegion(), seq);
        eventLog.add(event);
        log.debug("Stored item: key={}, version={}, seq={}", item.getKey(), item.getVersion(), seq);
        return event;
    }

    /**
     * Get a data item by key (excludes soft-deleted items).
     */
    public Optional<DataItem> get(String key) {
        DataItem item = dataStore.get(key);
        if (item != null && !item.isDeleted()) {
            return Optional.of(item);
        }
        return Optional.empty();
    }

    /**
     * Get a data item by key including deleted items.
     */
    public Optional<DataItem> getIncludingDeleted(String key) {
        return Optional.ofNullable(dataStore.get(key));
    }

    /**
     * Get all non-deleted data items.
     */
    public List<DataItem> getAll() {
        return dataStore.values().stream()
                .filter(item -> !item.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * Apply a sync event received from another region.
     * Uses version-based conflict resolution (higher version wins).
     */
    public boolean applySyncEvent(SyncEvent event) {
        DataItem incoming = event.getData();
        DataItem existing = dataStore.get(incoming.getKey());

        if (existing == null || incoming.getVersion() > existing.getVersion()) {
            dataStore.put(incoming.getKey(), incoming);
            log.debug("Applied sync event: type={}, key={}, version={}",
                    event.getType(), incoming.getKey(), incoming.getVersion());
            return true;
        } else if (incoming.getVersion() == existing.getVersion()
                && incoming.getTimestamp() > existing.getTimestamp()) {
            // Same version but newer timestamp - last-write-wins
            dataStore.put(incoming.getKey(), incoming);
            log.debug("Applied sync event (LWW): type={}, key={}, version={}",
                    event.getType(), incoming.getKey(), incoming.getVersion());
            return true;
        }

        log.debug("Skipped sync event (stale): type={}, key={}, incoming_v={}, existing_v={}",
                event.getType(), incoming.getKey(), incoming.getVersion(), existing.getVersion());
        return false;
    }

    /**
     * Get events after the given sequence number (used by slaves to pull new events).
     */
    public List<SyncEvent> getEventsAfter(long afterSequence) {
        return eventLog.stream()
                .filter(event -> event.getSequenceNumber() > afterSequence)
                .collect(Collectors.toList());
    }

    /**
     * Get the current sequence number (latest event sequence).
     */
    public long getCurrentSequence() {
        return sequenceCounter.get();
    }

    /**
     * Get total count of non-deleted items.
     */
    public long getItemCount() {
        return dataStore.values().stream()
                .filter(item -> !item.isDeleted())
                .count();
    }

    /**
     * Clear all data (used for testing or reset).
     */
    public void clear() {
        dataStore.clear();
        eventLog.clear();
        sequenceCounter.set(0);
    }
}
