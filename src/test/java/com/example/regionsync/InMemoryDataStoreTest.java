package com.example.regionsync;

import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.store.InMemoryDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 按数据归属划分 Master 的 InMemoryDataStore 单元测试。
 */
class InMemoryDataStoreTest {

    private InMemoryDataStore dataStore;

    @BeforeEach
    void setUp() {
        dataStore = new InMemoryDataStore();
    }

    @Test
    void testPutAndGet() {
        DataItem item = new DataItem("sys-config-1", "value1", DataCategory.SYSTEM_CONFIG, "NA");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        Optional<DataItem> retrieved = dataStore.get("sys-config-1");
        assertTrue(retrieved.isPresent());
        assertEquals("value1", retrieved.get().getValue());
        assertEquals(DataCategory.SYSTEM_CONFIG, retrieved.get().getCategory());
        assertEquals("NA", retrieved.get().getOwnerRegion());
    }

    @Test
    void testGetNonExistent() {
        Optional<DataItem> retrieved = dataStore.get("nonexistent");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testGetAll() {
        dataStore.put(new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("eu-job-1", "v2", DataCategory.JOB, "EU"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("cn-talent-1", "v3", DataCategory.TALENT, "CN"), SyncEvent.EventType.CREATE);

        List<DataItem> all = dataStore.getAll();
        assertEquals(3, all.size());
    }

    @Test
    void testGetAllByCategory() {
        dataStore.put(new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("eu-job-1", "v2", DataCategory.JOB, "EU"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("na-job-1", "v3", DataCategory.JOB, "NA"), SyncEvent.EventType.CREATE);

        List<DataItem> jobs = dataStore.getAllByCategory(DataCategory.JOB);
        assertEquals(2, jobs.size());

        List<DataItem> configs = dataStore.getAllByCategory(DataCategory.SYSTEM_CONFIG);
        assertEquals(1, configs.size());
    }

    @Test
    void testSoftDelete() {
        DataItem item = new DataItem("eu-job-1", "Engineer", DataCategory.JOB, "EU");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        DataItem deleted = item.asDeleted("EU");
        dataStore.put(deleted, SyncEvent.EventType.DELETE);

        assertFalse(dataStore.get("eu-job-1").isPresent());

        Optional<DataItem> withDeleted = dataStore.getIncludingDeleted("eu-job-1");
        assertTrue(withDeleted.isPresent());
        assertTrue(withDeleted.get().isDeleted());
    }

    @Test
    void testVersionUpdate() {
        DataItem item = new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA");
        dataStore.put(item, SyncEvent.EventType.CREATE);
        assertEquals(1, item.getVersion());

        DataItem updated = item.withUpdatedValue("v2", "NA");
        dataStore.put(updated, SyncEvent.EventType.UPDATE);
        assertEquals(2, updated.getVersion());
        assertEquals(DataCategory.SYSTEM_CONFIG, updated.getCategory());
        assertEquals("NA", updated.getOwnerRegion());

        Optional<DataItem> retrieved = dataStore.get("config-1");
        assertTrue(retrieved.isPresent());
        assertEquals("v2", retrieved.get().getValue());
    }

    @Test
    void testFinanceDataNotRecordedInEventLog() {
        // FINANCE 数据不应产生同步事件
        DataItem finance = new DataItem("cn-finance-1", "revenue", DataCategory.FINANCE, "CN");
        SyncEvent event = dataStore.put(finance, SyncEvent.EventType.CREATE);
        assertNull(event, "FINANCE data should not generate sync events");

        // 但数据本身应该存储成功
        Optional<DataItem> retrieved = dataStore.get("cn-finance-1");
        assertTrue(retrieved.isPresent());
        assertEquals("revenue", retrieved.get().getValue());

        // 事件日志应该是空的
        assertEquals(0, dataStore.getCurrentSequence());
        assertTrue(dataStore.getEventsAfter(0).isEmpty());
    }

    @Test
    void testNonFinanceDataRecordedInEventLog() {
        dataStore.put(new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("eu-job-1", "v2", DataCategory.JOB, "EU"), SyncEvent.EventType.CREATE);

        assertEquals(2, dataStore.getCurrentSequence());
        List<SyncEvent> events = dataStore.getEventsAfter(0);
        assertEquals(2, events.size());
    }

    @Test
    void testEventLogAfterSequence() {
        dataStore.put(new DataItem("k1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("k2", "v2", DataCategory.JOB, "EU"), SyncEvent.EventType.CREATE);

        List<SyncEvent> eventsAfterFirst = dataStore.getEventsAfter(1);
        assertEquals(1, eventsAfterFirst.size());
        assertEquals("k2", eventsAfterFirst.get(0).getData().getKey());
        assertEquals(DataCategory.JOB, eventsAfterFirst.get(0).getDataCategory());
    }

    @Test
    void testApplySyncEvent_NewItem() {
        DataItem item = new DataItem("eu-job-1", "Engineer", DataCategory.JOB, "EU");
        SyncEvent event = new SyncEvent(SyncEvent.EventType.CREATE, item, "EU", 1);

        boolean applied = dataStore.applySyncEvent(event);
        assertTrue(applied);

        Optional<DataItem> retrieved = dataStore.get("eu-job-1");
        assertTrue(retrieved.isPresent());
        assertEquals("Engineer", retrieved.get().getValue());
        assertEquals(DataCategory.JOB, retrieved.get().getCategory());
    }

    @Test
    void testApplySyncEvent_HigherVersionWins() {
        DataItem item = new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        DataItem updatedItem = item.withUpdatedValue("v2", "NA");
        SyncEvent event = new SyncEvent(SyncEvent.EventType.UPDATE, updatedItem, "NA", 2);

        boolean applied = dataStore.applySyncEvent(event);
        assertTrue(applied);

        Optional<DataItem> retrieved = dataStore.get("config-1");
        assertTrue(retrieved.isPresent());
        assertEquals("v2", retrieved.get().getValue());
    }

    @Test
    void testApplySyncEvent_LowerVersionRejected() {
        DataItem item = new DataItem("config-1", "v1", DataCategory.SYSTEM_CONFIG, "NA");
        DataItem updated = item.withUpdatedValue("v2", "NA");
        dataStore.put(updated, SyncEvent.EventType.CREATE);

        SyncEvent staleEvent = new SyncEvent(SyncEvent.EventType.UPDATE, item, "NA", 1);
        boolean applied = dataStore.applySyncEvent(staleEvent);
        assertFalse(applied);

        Optional<DataItem> retrieved = dataStore.get("config-1");
        assertTrue(retrieved.isPresent());
        assertEquals("v2", retrieved.get().getValue());
    }

    @Test
    void testItemCount() {
        assertEquals(0, dataStore.getItemCount());

        dataStore.put(new DataItem("k1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        assertEquals(1, dataStore.getItemCount());

        DataItem item2 = new DataItem("k2", "v2", DataCategory.JOB, "EU");
        dataStore.put(item2, SyncEvent.EventType.CREATE);
        assertEquals(2, dataStore.getItemCount());

        dataStore.put(item2.asDeleted("EU"), SyncEvent.EventType.DELETE);
        assertEquals(1, dataStore.getItemCount());
    }

    @Test
    void testClear() {
        dataStore.put(new DataItem("k1", "v1", DataCategory.SYSTEM_CONFIG, "NA"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("k2", "v2", DataCategory.JOB, "EU"), SyncEvent.EventType.CREATE);

        dataStore.clear();

        assertEquals(0, dataStore.getItemCount());
        assertEquals(0, dataStore.getCurrentSequence());
        assertTrue(dataStore.getAll().isEmpty());
    }

    @Test
    void testDataItemPreservesOwnerRegionAfterUpdate() {
        DataItem item = new DataItem("eu-job-1", "Engineer", DataCategory.JOB, "EU");
        // 即使由 NA 节点更新，ownerRegion 不变（仍然是 EU 的数据）
        DataItem updated = item.withUpdatedValue("Senior Engineer", "NA");
        assertEquals("EU", updated.getOwnerRegion());
        assertEquals("NA", updated.getSourceRegion());
        assertEquals(DataCategory.JOB, updated.getCategory());
    }
}
