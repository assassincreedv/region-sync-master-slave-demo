package com.example.regionsync;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.model.SyncEvent;
import com.example.regionsync.store.InMemoryDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the InMemoryDataStore, covering CRUD operations,
 * sync event generation, and conflict resolution.
 */
class InMemoryDataStoreTest {

    private InMemoryDataStore dataStore;

    @BeforeEach
    void setUp() {
        dataStore = new InMemoryDataStore();
    }

    @Test
    void testPutAndGet() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        Optional<DataItem> retrieved = dataStore.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("value1", retrieved.get().getValue());
        assertEquals("key1", retrieved.get().getKey());
    }

    @Test
    void testGetNonExistent() {
        Optional<DataItem> retrieved = dataStore.get("nonexistent");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testGetAll() {
        dataStore.put(new DataItem("key1", "value1", "region-1"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("key2", "value2", "region-1"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("key3", "value3", "region-1"), SyncEvent.EventType.CREATE);

        List<DataItem> all = dataStore.getAll();
        assertEquals(3, all.size());
    }

    @Test
    void testSoftDelete() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        DataItem deleted = item.asDeleted("region-1");
        dataStore.put(deleted, SyncEvent.EventType.DELETE);

        // Should not be returned by normal get
        Optional<DataItem> retrieved = dataStore.get("key1");
        assertFalse(retrieved.isPresent());

        // Should be returned by getIncludingDeleted
        Optional<DataItem> retrievedWithDeleted = dataStore.getIncludingDeleted("key1");
        assertTrue(retrievedWithDeleted.isPresent());
        assertTrue(retrievedWithDeleted.get().isDeleted());
    }

    @Test
    void testVersionUpdate() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        dataStore.put(item, SyncEvent.EventType.CREATE);
        assertEquals(1, item.getVersion());

        DataItem updated = item.withUpdatedValue("value2", "region-1");
        dataStore.put(updated, SyncEvent.EventType.UPDATE);
        assertEquals(2, updated.getVersion());

        Optional<DataItem> retrieved = dataStore.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("value2", retrieved.get().getValue());
        assertEquals(2, retrieved.get().getVersion());
    }

    @Test
    void testEventLog() {
        dataStore.put(new DataItem("key1", "value1", "region-1"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("key2", "value2", "region-1"), SyncEvent.EventType.CREATE);

        List<SyncEvent> allEvents = dataStore.getEventsAfter(0);
        assertEquals(2, allEvents.size());

        List<SyncEvent> eventsAfterFirst = dataStore.getEventsAfter(1);
        assertEquals(1, eventsAfterFirst.size());
        assertEquals("key2", eventsAfterFirst.get(0).getData().getKey());
    }

    @Test
    void testSequenceCounter() {
        assertEquals(0, dataStore.getCurrentSequence());

        dataStore.put(new DataItem("key1", "value1", "region-1"), SyncEvent.EventType.CREATE);
        assertEquals(1, dataStore.getCurrentSequence());

        dataStore.put(new DataItem("key2", "value2", "region-1"), SyncEvent.EventType.CREATE);
        assertEquals(2, dataStore.getCurrentSequence());
    }

    @Test
    void testApplySyncEvent_NewItem() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        SyncEvent event = new SyncEvent(SyncEvent.EventType.CREATE, item, "region-1", 1);

        boolean applied = dataStore.applySyncEvent(event);
        assertTrue(applied);

        Optional<DataItem> retrieved = dataStore.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("value1", retrieved.get().getValue());
    }

    @Test
    void testApplySyncEvent_HigherVersion() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        dataStore.put(item, SyncEvent.EventType.CREATE);

        DataItem updatedItem = item.withUpdatedValue("value2", "region-1");
        SyncEvent event = new SyncEvent(SyncEvent.EventType.UPDATE, updatedItem, "region-1", 2);

        boolean applied = dataStore.applySyncEvent(event);
        assertTrue(applied);

        Optional<DataItem> retrieved = dataStore.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("value2", retrieved.get().getValue());
    }

    @Test
    void testApplySyncEvent_LowerVersion_Rejected() {
        DataItem item = new DataItem("key1", "value1", "region-1");
        DataItem updated = item.withUpdatedValue("value2", "region-1");
        dataStore.put(updated, SyncEvent.EventType.CREATE);

        // Try to apply an older version
        SyncEvent staleEvent = new SyncEvent(SyncEvent.EventType.UPDATE, item, "region-1", 1);

        boolean applied = dataStore.applySyncEvent(staleEvent);
        assertFalse(applied);

        // Value should still be the newer version
        Optional<DataItem> retrieved = dataStore.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("value2", retrieved.get().getValue());
    }

    @Test
    void testItemCount() {
        assertEquals(0, dataStore.getItemCount());

        dataStore.put(new DataItem("key1", "value1", "region-1"), SyncEvent.EventType.CREATE);
        assertEquals(1, dataStore.getItemCount());

        DataItem item2 = new DataItem("key2", "value2", "region-1");
        dataStore.put(item2, SyncEvent.EventType.CREATE);
        assertEquals(2, dataStore.getItemCount());

        // Soft delete should reduce count
        dataStore.put(item2.asDeleted("region-1"), SyncEvent.EventType.DELETE);
        assertEquals(1, dataStore.getItemCount());
    }

    @Test
    void testClear() {
        dataStore.put(new DataItem("key1", "value1", "region-1"), SyncEvent.EventType.CREATE);
        dataStore.put(new DataItem("key2", "value2", "region-1"), SyncEvent.EventType.CREATE);

        dataStore.clear();

        assertEquals(0, dataStore.getItemCount());
        assertEquals(0, dataStore.getCurrentSequence());
        assertTrue(dataStore.getAll().isEmpty());
    }
}
