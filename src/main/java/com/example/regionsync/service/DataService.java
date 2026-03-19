package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.store.InMemoryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 数据服务 — 按数据归属划分 Master 的 CRUD 操作。
 *
 * <p>核心规则：
 * <ul>
 *   <li>SYSTEM_CONFIG / ROLE_PERMISSION → 全局 Master 固定为 NA，其他 Region 只读</li>
 *   <li>JOB / TALENT → 各 Region 是自己数据的 Master，其他 Region 只读</li>
 *   <li>FINANCE → 各 Region 本地读写，不跨区同步（合规要求）</li>
 * </ul>
 *
 * <p>写操作路由逻辑：
 * <ol>
 *   <li>本节点是该数据的 owner → 直接本地写入，然后推送给其他 peer</li>
 *   <li>本节点不是该数据的 owner → 通过 {@link RegionWriteRouter} 转发到 owner region</li>
 * </ol>
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
     * 本地创建数据（仅当本节点是该数据的 Master 时调用）。
     */
    public DataItem createLocal(String key, String value, DataCategory category, String ownerRegion) {
        ensureOwner(category, ownerRegion, "CREATE");
        DataItem item = new DataItem(key, value, category, ownerRegion);
        item.setSourceRegion(regionConfig.getId());
        SyncEvent event = dataStore.put(item, SyncEvent.EventType.CREATE);
        log.info("Created item locally: key={}, category={}, ownerRegion={}", key, category, ownerRegion);
        if (event != null) {
            syncService.pushEventToPeers(event);
        }
        return item;
    }

    /**
     * 本地更新数据。
     */
    public DataItem updateLocal(String key, String newValue) {
        Optional<DataItem> existing = dataStore.get(key);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Data item not found: " + key);
        }
        DataItem item = existing.get();
        ensureOwner(item.getCategory(), item.getOwnerRegion(), "UPDATE");

        DataItem updated = item.withUpdatedValue(newValue, regionConfig.getId());
        SyncEvent event = dataStore.put(updated, SyncEvent.EventType.UPDATE);
        log.info("Updated item locally: key={}, version={}", key, updated.getVersion());
        if (event != null) {
            syncService.pushEventToPeers(event);
        }
        return updated;
    }

    /**
     * 本地删除数据（软删除）。
     */
    public void deleteLocal(String key) {
        Optional<DataItem> existing = dataStore.get(key);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Data item not found: " + key);
        }
        DataItem item = existing.get();
        ensureOwner(item.getCategory(), item.getOwnerRegion(), "DELETE");

        DataItem deleted = item.asDeleted(regionConfig.getId());
        SyncEvent event = dataStore.put(deleted, SyncEvent.EventType.DELETE);
        log.info("Deleted item locally: key={}", key);
        if (event != null) {
            syncService.pushEventToPeers(event);
        }
    }

    /** 按 key 读取数据（所有 Region 都可以读本地副本） */
    public Optional<DataItem> get(String key) {
        return dataStore.get(key);
    }

    /** 获取所有数据 */
    public List<DataItem> getAll() {
        return dataStore.getAll();
    }

    /** 按 category 获取数据 */
    public List<DataItem> getAllByCategory(DataCategory category) {
        return dataStore.getAllByCategory(category);
    }

    /**
     * 确保本节点是该数据的 Owner（Master）。
     */
    private void ensureOwner(DataCategory category, String ownerRegion, String operation) {
        if (!regionConfig.isOwnerOf(category, ownerRegion)) {
            throw new UnsupportedOperationException(
                    operation + " not allowed on this node [" + regionConfig.getId() +
                    "] for category=" + category + ", ownerRegion=" + ownerRegion +
                    ". Write must be routed to the owner region.");
        }
    }
}
