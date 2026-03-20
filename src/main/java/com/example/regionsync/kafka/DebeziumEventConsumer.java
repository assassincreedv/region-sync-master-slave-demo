package com.example.regionsync.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Debezium CDC 事件消费者 — 消费 Debezium 从 MySQL binlog 捕获的变更事件。
 *
 * <p>Debezium 通过监听 MySQL 的 binlog，将所有 company 表的变更（INSERT/UPDATE/DELETE）
 * 自动发布到 Kafka Topic（默认: dbserver.region_db.company）。
 *
 * <p>此消费者用于：
 * <ul>
 *   <li>审计日志 — 记录所有通过 CDC 捕获的数据变更</li>
 *   <li>监控 — 追踪数据库级别的实际变更</li>
 *   <li>验证 — 确认应用层同步与数据库层 CDC 的一致性</li>
 * </ul>
 *
 * <p>注意：实际的跨区数据同步通过 {@link KafkaConsumerService} 消费应用层事件完成，
 * 此处的 Debezium CDC 事件主要用于监控和审计。
 */
@Service
public class DebeziumEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DebeziumEventConsumer.class);

    private final ObjectMapper objectMapper;

    public DebeziumEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 Debezium CDC 事件。
     * Topic 名称格式: dbserver.region_db.company（由 Debezium connector 配置决定）
     */
    @KafkaListener(topics = "${debezium.topic:dbserver.region_db.company}",
                   groupId = "${spring.kafka.consumer.group-id:region-sync-group}-debezium",
                   autoStartup = "${debezium.consumer.enabled:false}")
    public void consumeDebeziumEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            JsonNode payload = event.path("payload");

            String operation = payload.path("op").asText();
            String operationName = switch (operation) {
                case "c" -> "INSERT";
                case "u" -> "UPDATE";
                case "d" -> "DELETE";
                case "r" -> "SNAPSHOT_READ";
                default -> "UNKNOWN(" + operation + ")";
            };

            JsonNode after = payload.path("after");
            JsonNode before = payload.path("before");

            String bizKey = "N/A";
            String name = "N/A";

            if (!after.isMissingNode() && !after.isNull()) {
                bizKey = after.path("biz_key").asText("N/A");
                name = after.path("name").asText("N/A");
            } else if (!before.isMissingNode() && !before.isNull()) {
                bizKey = before.path("biz_key").asText("N/A");
                name = before.path("name").asText("N/A");
            }

            JsonNode source = payload.path("source");
            String dbName = source.path("db").asText("N/A");
            String tableName = source.path("table").asText("N/A");

            log.info("[Debezium CDC] operation={}, db={}, table={}, bizKey={}, name={}",
                    operationName, dbName, tableName, bizKey, name);

        } catch (Exception e) {
            log.warn("[Debezium CDC] Failed to parse event: {}", e.getMessage());
        }
    }
}
