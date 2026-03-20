package com.example.regionsync.kafka;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.entity.Company;
import com.example.regionsync.entity.SyncEventLog;
import com.example.regionsync.repository.SyncEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 生产者服务 — 将同步事件发送到 Kafka Topic。
 *
 * <p>当 Master Region 写入 Company 数据后，通过此服务将变更事件推送到 Kafka，
 * 其他 Region 的消费者监听并同步到本地数据库。
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    public static final String TOPIC_COMPANY_SYNC = "region-sync-company";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RegionConfig regionConfig;
    private final SyncEventLogRepository syncEventLogRepository;
    private final ObjectMapper objectMapper;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate,
                                RegionConfig regionConfig,
                                SyncEventLogRepository syncEventLogRepository,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.regionConfig = regionConfig;
        this.syncEventLogRepository = syncEventLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布 Company 变更事件到 Kafka。
     *
     * @param company   变更后的 Company 实体
     * @param eventType 事件类型: CREATE, UPDATE, DELETE
     */
    public void publishCompanyEvent(Company company, String eventType) {
        try {
            long seq = sequenceCounter.incrementAndGet();

            // 构造事件 payload
            CompanySyncMessage message = new CompanySyncMessage();
            message.setEventType(eventType);
            message.setSourceRegion(regionConfig.getId());
            message.setSequenceNumber(seq);
            message.setCompany(company);

            String payload = objectMapper.writeValueAsString(message);

            // 记录 SyncEventLog（SENT 状态）
            SyncEventLog eventLog = SyncEventLog.createSentLog(
                    eventType, "COMPANY", company.getId(), company.getBizKey(),
                    regionConfig.getId(), seq, payload);
            eventLog.setKafkaTopic(TOPIC_COMPANY_SYNC);
            syncEventLogRepository.save(eventLog);

            // 发送到 Kafka，使用 bizKey 作为 partition key 保证同一条数据的事件有序
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(TOPIC_COMPANY_SYNC, company.getBizKey(), payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send event to Kafka: topic={}, bizKey={}, error={}",
                            TOPIC_COMPANY_SYNC, company.getBizKey(), ex.getMessage());
                    eventLog.setStatus("FAILED");
                    eventLog.setErrorMessage(ex.getMessage());
                    syncEventLogRepository.save(eventLog);
                } else {
                    log.info("Published event to Kafka: topic={}, partition={}, offset={}, eventType={}, bizKey={}",
                            TOPIC_COMPANY_SYNC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            eventType, company.getBizKey());
                    eventLog.setKafkaPartition(result.getRecordMetadata().partition());
                    eventLog.setKafkaOffset(result.getRecordMetadata().offset());
                    syncEventLogRepository.save(eventLog);
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize company event: {}", e.getMessage());
        }
    }

    public long getCurrentSequence() {
        return sequenceCounter.get();
    }

    /**
     * Kafka 消息体 — 包含事件元数据和 Company 数据。
     */
    public static class CompanySyncMessage {
        private String eventType;
        private String sourceRegion;
        private long sequenceNumber;
        private Company company;

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSourceRegion() { return sourceRegion; }
        public void setSourceRegion(String sourceRegion) { this.sourceRegion = sourceRegion; }

        public long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

        public Company getCompany() { return company; }
        public void setCompany(Company company) { this.company = company; }
    }
}
