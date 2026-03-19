package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.store.InMemoryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for CRUD operations on data items.
 * Write operations are only allowed on the MASTER node.
 * Read operations are allowed on both MASTER and SLAVE nodes.
 *
 * When a write operation occurs on the master, a SyncEvent is created
 * and pushed to slave regions by the SyncService.
 */
@Service
public class DataService {

    private static final Logger log = LoggerFactory.getLogger(DataService.class);

    private final InMemoryDataStore dataStore;
    private final RegionConfig regionConfig;
    private final SyncService syncService;

    public DataService(InMemoryDataStore dataStore, RegionConfig regionConfig, SyncService syncService) {
        this.dataStore = dataStore;
        this.regionConfig = regionConfig;
        this.syncService = syncService;
    }

    /**
     * Create a new data item. Only allowed on MASTER.
     */
    public DataItem create(String key, String value) {
        ensureMaster("CREATE");
        DataItem item = new DataItem(key, value, regionConfig.getId());
        SyncEvent event = dataStore.put(item, SyncEvent.EventType.CREATE);
        log.info("Created item: key={}, id={}", key, item.getId());
        syncService.pushEventToSlaves(event);
        return item;
    }

    /**
     * Update an existing data item. Only allowed on MASTER.
     */
    public DataItem update(String key, String newValue) {
        ensureMaster("UPDATE");
        Optional<DataItem> existing = dataStore.get(key);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Data item not found: " + key);
        }
        DataItem updated = existing.get().withUpdatedValue(newValue, regionConfig.getId());
        SyncEvent event = dataStore.put(updated, SyncEvent.EventType.UPDATE);
        log.info("Updated item: key={}, version={}", key, updated.getVersion());
        syncService.pushEventToSlaves(event);
        return updated;
    }

    /**
     * Delete a data item (soft delete). Only allowed on MASTER.
     */
    public void delete(String key) {
        ensureMaster("DELETE");
        Optional<DataItem> existing = dataStore.get(key);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Data item not found: " + key);
        }
        DataItem deleted = existing.get().asDeleted(regionConfig.getId());
        SyncEvent event = dataStore.put(deleted, SyncEvent.EventType.DELETE);
        log.info("Deleted item: key={}", key);
        syncService.pushEventToSlaves(event);
    }

    /**
     * Get a data item by key. Allowed on both MASTER and SLAVE.
     */
    public Optional<DataItem> get(String key) {
        return dataStore.get(key);
    }

    /**
     * Get all data items. Allowed on both MASTER and SLAVE.
     */
    public List<DataItem> getAll() {
        return dataStore.getAll();
    }

    /**
     * Ensure the current node is the MASTER for write operations.
     */
    private void ensureMaster(String operation) {
        if (!regionConfig.isMaster()) {
            throw new UnsupportedOperationException(
                    operation + " operation not allowed on SLAVE node [" + regionConfig.getId() +
                    "]. Write operations must be sent to the MASTER node.");
        }
    }
}
