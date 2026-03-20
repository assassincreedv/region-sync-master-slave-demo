package com.example.regionsync.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 同步事件日志实体 — 记录所有跨区同步事件到数据库。
 *
 * <p>每当 Master Region 写入数据后，会在本地数据库记录一条 SyncEventLog，
 * 并通过 Kafka 推送给其他 Region。接收方 Region 也会记录一条 APPLIED 状态的日志。
 */
@Entity
@Table(name = "sync_event_log", indexes = {
        @Index(name = "idx_sync_event_source_region", columnList = "source_region"),
        @Index(name = "idx_sync_event_status", columnList = "status"),
        @Index(name = "idx_sync_event_created_at", columnList = "created_at"),
        @Index(name = "idx_sync_event_entity_id", columnList = "entity_id")
})
public class SyncEventLog {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    /** 事件类型: CREATE, UPDATE, DELETE */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    /** 实体类型: COMPANY */
    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    /** 关联的实体 ID */
    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    /** 关联的业务键 */
    @Column(name = "entity_biz_key", length = 128)
    private String entityBizKey;

    /** 事件源 Region */
    @Column(name = "source_region", nullable = false, length = 16)
    private String sourceRegion;

    /** 目标 Region（对于接收方，填本节点 ID） */
    @Column(name = "target_region", length = 16)
    private String targetRegion;

    /** 序列号 */
    @Column(name = "sequence_number")
    private long sequenceNumber;

    /** 事件负载（JSON 格式） */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** 状态: PENDING, SENT, APPLIED, FAILED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Kafka topic */
    @Column(name = "kafka_topic", length = 128)
    private String kafkaTopic;

    /** Kafka partition */
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    /** Kafka offset */
    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    public SyncEventLog() {
        this.eventId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 创建一条 SENT 状态的事件日志（Master 端写入后调用）。
     */
    public static SyncEventLog createSentLog(String eventType, String entityType,
                                             String entityId, String entityBizKey,
                                             String sourceRegion, long sequenceNumber,
                                             String payload) {
        SyncEventLog log = new SyncEventLog();
        log.eventType = eventType;
        log.entityType = entityType;
        log.entityId = entityId;
        log.entityBizKey = entityBizKey;
        log.sourceRegion = sourceRegion;
        log.sequenceNumber = sequenceNumber;
        log.payload = payload;
        log.status = "SENT";
        return log;
    }

    /**
     * 创建一条 APPLIED 状态的事件日志（Slave 端接收并应用后调用）。
     */
    public static SyncEventLog createAppliedLog(String eventType, String entityType,
                                                String entityId, String entityBizKey,
                                                String sourceRegion, String targetRegion,
                                                long sequenceNumber, String payload) {
        SyncEventLog log = new SyncEventLog();
        log.eventType = eventType;
        log.entityType = entityType;
        log.entityId = entityId;
        log.entityBizKey = entityBizKey;
        log.sourceRegion = sourceRegion;
        log.targetRegion = targetRegion;
        log.sequenceNumber = sequenceNumber;
        log.payload = payload;
        log.status = "APPLIED";
        log.processedAt = LocalDateTime.now();
        return log;
    }

    // ── Getters & Setters ──

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityBizKey() { return entityBizKey; }
    public void setEntityBizKey(String entityBizKey) { this.entityBizKey = entityBizKey; }

    public String getSourceRegion() { return sourceRegion; }
    public void setSourceRegion(String sourceRegion) { this.sourceRegion = sourceRegion; }

    public String getTargetRegion() { return targetRegion; }
    public void setTargetRegion(String targetRegion) { this.targetRegion = targetRegion; }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getKafkaTopic() { return kafkaTopic; }
    public void setKafkaTopic(String kafkaTopic) { this.kafkaTopic = kafkaTopic; }

    public Integer getKafkaPartition() { return kafkaPartition; }
    public void setKafkaPartition(Integer kafkaPartition) { this.kafkaPartition = kafkaPartition; }

    public Long getKafkaOffset() { return kafkaOffset; }
    public void setKafkaOffset(Long kafkaOffset) { this.kafkaOffset = kafkaOffset; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "SyncEventLog{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", sourceRegion='" + sourceRegion + '\'' +
                ", status='" + status + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
