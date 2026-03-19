package com.example.regionsync.model;

import java.util.UUID;

/**
 * Represents a synchronization event that is replicated from master to slave regions.
 * Each event captures a data change operation (CREATE, UPDATE, DELETE).
 */
public class SyncEvent {

    public enum EventType {
        CREATE, UPDATE, DELETE
    }

    private String eventId;
    private EventType type;
    private DataItem data;
    private long timestamp;
    private String sourceRegion;
    private long sequenceNumber;

    public SyncEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public SyncEvent(EventType type, DataItem data, String sourceRegion, long sequenceNumber) {
        this();
        this.type = type;
        this.data = data;
        this.sourceRegion = sourceRegion;
        this.sequenceNumber = sequenceNumber;
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public DataItem getData() {
        return data;
    }

    public void setData(DataItem data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(String sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "SyncEvent{" +
                "eventId='" + eventId + '\'' +
                ", type=" + type +
                ", data=" + data +
                ", sequenceNumber=" + sequenceNumber +
                ", sourceRegion='" + sourceRegion + '\'' +
                '}';
    }
}
