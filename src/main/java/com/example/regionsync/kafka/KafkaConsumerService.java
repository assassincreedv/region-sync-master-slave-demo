package com.example.regionsync.kafka;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.entity.Company;
import com.example.regionsync.entity.SyncEventLog;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.repository.CompanyRepository;
import com.example.regionsync.repository.SyncEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Kafka 消费者服务 — 消费来自其他 Region 的同步事件，写入本地数据库。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>从 Kafka 消费 Company 变更事件</li>
 *   <li>过滤掉来自本节点的事件（避免自己消费自己的事件）</li>
 *   <li>按版本号比较，高版本覆盖低版本（冲突解决）</li>
 *   <li>写入本地 MySQL 数据库</li>
 *   <li>记录 SyncEventLog（APPLIED 状态）</li>
 * </ol>
 *
 * <p>FINANCE 类数据不跨区同步（合规要求），消费者会自动跳过。
 */
@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final CompanyRepository companyRepository;
    private final SyncEventLogRepository syncEventLogRepository;
    private final RegionConfig regionConfig;
    private final ObjectMapper objectMapper;

    public KafkaConsumerService(CompanyRepository companyRepository,
                                SyncEventLogRepository syncEventLogRepository,
                                RegionConfig regionConfig,
                                ObjectMapper objectMapper) {
        this.companyRepository = companyRepository;
        this.syncEventLogRepository = syncEventLogRepository;
        this.regionConfig = regionConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 Company 同步事件。
     * 每个 Region 使用相同的 consumer group（region-${REGION_ID}），
     * 确保每个 Region 只消费一次。
     */
    @KafkaListener(topics = KafkaProducerService.TOPIC_COMPANY_SYNC,
                   groupId = "${spring.kafka.consumer.group-id:region-sync-group}")
    public void consumeCompanySyncEvent(String message) {
        try {
            KafkaProducerService.CompanySyncMessage syncMessage =
                    objectMapper.readValue(message, KafkaProducerService.CompanySyncMessage.class);

            // 过滤掉来自本节点的事件
            if (regionConfig.getId().equalsIgnoreCase(syncMessage.getSourceRegion())) {
                log.debug("Skipping event from self: sourceRegion={}", syncMessage.getSourceRegion());
                return;
            }

            Company incomingCompany = syncMessage.getCompany();

            // FINANCE 数据不跨区同步
            if (incomingCompany.getCategory() == DataCategory.FINANCE) {
                log.debug("Skipping FINANCE data sync: bizKey={}", incomingCompany.getBizKey());
                return;
            }

            log.info("Received sync event: type={}, sourceRegion={}, bizKey={}, version={}",
                    syncMessage.getEventType(), syncMessage.getSourceRegion(),
                    incomingCompany.getBizKey(), incomingCompany.getVersion());

            // 查找本地是否已有该数据
            Optional<Company> existingOpt = companyRepository.findByBizKey(incomingCompany.getBizKey());

            boolean applied = false;

            if (existingOpt.isEmpty()) {
                // 本地没有，直接插入
                companyRepository.save(incomingCompany);
                applied = true;
                log.info("Applied sync event (new): bizKey={}, version={}",
                        incomingCompany.getBizKey(), incomingCompany.getVersion());
            } else {
                Company existing = existingOpt.get();
                // 版本号高者胜；版本号相同时时间戳新者胜
                if (incomingCompany.getVersion() > existing.getVersion()) {
                    applyUpdate(existing, incomingCompany);
                    applied = true;
                    log.info("Applied sync event (higher version): bizKey={}, v{}->v{}",
                            incomingCompany.getBizKey(), existing.getVersion(), incomingCompany.getVersion());
                } else if (incomingCompany.getVersion() == existing.getVersion()
                        && incomingCompany.getUpdatedAt() != null
                        && existing.getUpdatedAt() != null
                        && incomingCompany.getUpdatedAt().isAfter(existing.getUpdatedAt())) {
                    applyUpdate(existing, incomingCompany);
                    applied = true;
                    log.info("Applied sync event (LWW): bizKey={}, version={}",
                            incomingCompany.getBizKey(), incomingCompany.getVersion());
                } else {
                    log.debug("Skipped stale sync event: bizKey={}, incoming_v={}, existing_v={}",
                            incomingCompany.getBizKey(), incomingCompany.getVersion(), existing.getVersion());
                }
            }

            // 记录 SyncEventLog
            String status = applied ? "APPLIED" : "SKIPPED";
            SyncEventLog eventLog = SyncEventLog.createAppliedLog(
                    syncMessage.getEventType(), "COMPANY",
                    incomingCompany.getId(), incomingCompany.getBizKey(),
                    syncMessage.getSourceRegion(), regionConfig.getId(),
                    syncMessage.getSequenceNumber(), message);
            eventLog.setStatus(status);
            syncEventLogRepository.save(eventLog);

        } catch (Exception e) {
            log.error("Failed to process sync event: {}", e.getMessage(), e);
            // 记录失败的事件日志
            try {
                SyncEventLog failedLog = new SyncEventLog();
                failedLog.setEventType("UNKNOWN");
                failedLog.setEntityType("COMPANY");
                failedLog.setEntityId("UNKNOWN");
                failedLog.setSourceRegion("UNKNOWN");
                failedLog.setTargetRegion(regionConfig.getId());
                failedLog.setPayload(message);
                failedLog.setStatus("FAILED");
                failedLog.setErrorMessage(e.getMessage());
                failedLog.setProcessedAt(LocalDateTime.now());
                syncEventLogRepository.save(failedLog);
            } catch (Exception ex) {
                log.error("Failed to record error log: {}", ex.getMessage());
            }
        }
    }

    /**
     * 将远程同步的数据应用到本地已有记录上。
     */
    private void applyUpdate(Company existing, Company incoming) {
        existing.setName(incoming.getName());
        existing.setAddress(incoming.getAddress());
        existing.setIndustry(incoming.getIndustry());
        existing.setCategory(incoming.getCategory());
        existing.setOwnerRegion(incoming.getOwnerRegion());
        existing.setSourceRegion(incoming.getSourceRegion());
        existing.setVersion(incoming.getVersion());
        existing.setUpdatedAt(incoming.getUpdatedAt());
        existing.setDeleted(incoming.isDeleted());
        companyRepository.save(existing);
    }
}
