package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.entity.Company;
import com.example.regionsync.kafka.KafkaProducerService;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Company 业务服务 — 使用真实数据库（MySQL）存储，通过 Kafka 同步到其他 Region。
 *
 * <p>核心规则：
 * <ul>
 *   <li>SYSTEM_CONFIG / ROLE_PERMISSION → 全局 Master 固定为 NA，其他 Region 只读</li>
 *   <li>JOB / TALENT → 各 Region 是自己数据的 Master，其他 Region 只读</li>
 *   <li>FINANCE → 各 Region 本地读写，不跨区同步（合规要求）</li>
 * </ul>
 *
 * <p>写操作流程：
 * <ol>
 *   <li>验证本节点是否是该数据的 Owner</li>
 *   <li>写入本地 MySQL 数据库（company 表）</li>
 *   <li>通过 Kafka 发布变更事件（FINANCE 除外）</li>
 *   <li>其他 Region 的 KafkaConsumerService 消费事件并同步到本地数据库</li>
 * </ol>
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;
    private final RegionConfig regionConfig;
    private final KafkaProducerService kafkaProducerService;

    public CompanyService(CompanyRepository companyRepository,
                          RegionConfig regionConfig,
                          KafkaProducerService kafkaProducerService) {
        this.companyRepository = companyRepository;
        this.regionConfig = regionConfig;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * 本地创建 Company（仅当本节点是该数据的 Master 时调用）。
     */
    @Transactional
    public Company createLocal(String bizKey, String name, String address, String industry,
                               DataCategory category, String ownerRegion) {
        ensureOwner(category, ownerRegion, "CREATE");

        // 检查是否已存在
        Optional<Company> existing = companyRepository.findByBizKey(bizKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Company with bizKey already exists: " + bizKey);
        }

        Company company = new Company(bizKey, name, address, industry, category, ownerRegion);
        company.setSourceRegion(regionConfig.getId());
        company = companyRepository.save(company);

        log.info("Created company in DB: bizKey={}, category={}, ownerRegion={}", bizKey, category, ownerRegion);

        // 非 FINANCE 数据发布到 Kafka 同步
        if (category != DataCategory.FINANCE) {
            kafkaProducerService.publishCompanyEvent(company, "CREATE");
        }

        return company;
    }

    /**
     * 本地更新 Company。
     */
    @Transactional
    public Company updateLocal(String bizKey, String name, String address, String industry) {
        Company company = companyRepository.findByBizKeyAndDeletedFalse(bizKey)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + bizKey));

        ensureOwner(company.getCategory(), company.getOwnerRegion(), "UPDATE");

        if (name != null) company.setName(name);
        if (address != null) company.setAddress(address);
        if (industry != null) company.setIndustry(industry);
        company.setVersion(company.getVersion() + 1);
        company.setUpdatedAt(LocalDateTime.now());
        company.setSourceRegion(regionConfig.getId());

        company = companyRepository.save(company);
        log.info("Updated company in DB: bizKey={}, version={}", bizKey, company.getVersion());

        // 非 FINANCE 数据发布到 Kafka 同步
        if (company.getCategory() != DataCategory.FINANCE) {
            kafkaProducerService.publishCompanyEvent(company, "UPDATE");
        }

        return company;
    }

    /**
     * 本地删除 Company（软删除）。
     */
    @Transactional
    public void deleteLocal(String bizKey) {
        Company company = companyRepository.findByBizKeyAndDeletedFalse(bizKey)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + bizKey));

        ensureOwner(company.getCategory(), company.getOwnerRegion(), "DELETE");

        company.setDeleted(true);
        company.setVersion(company.getVersion() + 1);
        company.setUpdatedAt(LocalDateTime.now());
        company.setSourceRegion(regionConfig.getId());

        company = companyRepository.save(company);
        log.info("Deleted company in DB (soft): bizKey={}", bizKey);

        // 非 FINANCE 数据发布到 Kafka 同步
        if (company.getCategory() != DataCategory.FINANCE) {
            kafkaProducerService.publishCompanyEvent(company, "DELETE");
        }
    }

    /** 按 bizKey 查询（排除已删除） */
    public Optional<Company> getByBizKey(String bizKey) {
        return companyRepository.findByBizKeyAndDeletedFalse(bizKey);
    }

    /** 查询所有未删除数据 */
    public List<Company> getAll() {
        return companyRepository.findByDeletedFalse();
    }

    /** 按 category 查询 */
    public List<Company> getAllByCategory(DataCategory category) {
        return companyRepository.findByCategoryAndDeletedFalse(category);
    }

    /** 按 ownerRegion 查询 */
    public List<Company> getAllByOwnerRegion(String ownerRegion) {
        return companyRepository.findByOwnerRegionAndDeletedFalse(ownerRegion);
    }

    /** 获取当前节点 Region ID */
    public String getCurrentRegionId() {
        return regionConfig.getId();
    }

    /** 未删除数据总数 */
    public long getItemCount() {
        return companyRepository.countByDeletedFalse();
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
