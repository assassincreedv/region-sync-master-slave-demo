package com.example.regionsync.controller;

import com.example.regionsync.entity.Company;
import com.example.regionsync.entity.SyncEventLog;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.repository.SyncEventLogRepository;
import com.example.regionsync.service.CompanyService;
import com.example.regionsync.service.RegionWriteRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Company REST 控制器 — 使用真实数据库的 CRUD 接口。
 *
 * <p>写操作路由逻辑（与 DataController 相同）：
 * <ul>
 *   <li>如果本节点是该数据的 Owner → 本地写入 MySQL</li>
 *   <li>如果本节点不是 Owner → 通过 {@link RegionWriteRouter} 转发到 Owner Region</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * # 在 NA 创建 Company
 * curl -X POST http://localhost:8081/api/company \
 *   -H "Content-Type: application/json" \
 *   -d '{"bizKey":"na-tech-001","name":"TechCorp NA","address":"NYC","industry":"Technology","category":"JOB","ownerRegion":"NA"}'
 *
 * # 在 EU 创建 Company
 * curl -X POST http://localhost:8082/api/company \
 *   -H "Content-Type: application/json" \
 *   -d '{"bizKey":"eu-finance-001","name":"FinanceEU","address":"London","industry":"Finance","category":"JOB","ownerRegion":"EU"}'
 *
 * # 在 CN 查看已同步的数据
 * curl http://localhost:8083/api/company/na-tech-001
 * </pre>
 */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyService companyService;
    private final RegionWriteRouter writeRouter;
    private final SyncEventLogRepository syncEventLogRepository;

    public CompanyController(CompanyService companyService,
                             RegionWriteRouter writeRouter,
                             SyncEventLogRepository syncEventLogRepository) {
        this.companyService = companyService;
        this.writeRouter = writeRouter;
        this.syncEventLogRepository = syncEventLogRepository;
    }

    /**
     * 创建 Company。
     * POST /api/company
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        try {
            String bizKey = request.get("bizKey");
            String name = request.get("name");
            String address = request.get("address");
            String industry = request.get("industry");
            String categoryStr = request.get("category");
            String ownerRegion = request.get("ownerRegion");

            if (bizKey == null || bizKey.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "bizKey is required"));
            }
            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
            }
            if (categoryStr == null || categoryStr.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "category is required"));
            }

            DataCategory category;
            try {
                category = DataCategory.valueOf(categoryStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Invalid category: " + categoryStr + ". Valid values: " +
                        java.util.Arrays.toString(DataCategory.values())));
            }

            if (ownerRegion == null || ownerRegion.isBlank()) {
                ownerRegion = companyService.getCurrentRegionId();
            }

            if (writeRouter.shouldWriteLocally(category, ownerRegion)) {
                Company company = companyService.createLocal(bizKey, name, address, industry, category, ownerRegion);
                return ResponseEntity.status(HttpStatus.CREATED).body(company);
            } else {
                log.info("Forwarding Company CREATE to owner region for category={}, ownerRegion={}",
                        category, ownerRegion);
                // 复用 RegionWriteRouter 的转发逻辑，发送到 /api/company
                var item = writeRouter.forwardCompanyCreate(category, ownerRegion, request);
                return ResponseEntity.status(HttpStatus.CREATED).body(item);
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating company", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按 bizKey 读取 Company。
     * GET /api/company/{bizKey}
     */
    @GetMapping("/{bizKey}")
    public ResponseEntity<?> get(@PathVariable String bizKey) {
        Optional<Company> company = companyService.getByBizKey(bizKey);
        if (company.isPresent()) {
            return ResponseEntity.ok(company.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Company not found: " + bizKey));
    }

    /**
     * 获取所有 Company。
     * GET /api/company
     * GET /api/company?category=JOB
     * GET /api/company?ownerRegion=NA
     */
    @GetMapping
    public ResponseEntity<List<Company>> getAll(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String ownerRegion) {
        if (category != null && !category.isBlank()) {
            try {
                DataCategory cat = DataCategory.valueOf(category.toUpperCase());
                return ResponseEntity.ok(companyService.getAllByCategory(cat));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (ownerRegion != null && !ownerRegion.isBlank()) {
            return ResponseEntity.ok(companyService.getAllByOwnerRegion(ownerRegion));
        }
        return ResponseEntity.ok(companyService.getAll());
    }

    /**
     * 更新 Company。
     * PUT /api/company/{bizKey}
     */
    @PutMapping("/{bizKey}")
    public ResponseEntity<?> update(@PathVariable String bizKey, @RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String address = request.get("address");
            String industry = request.get("industry");

            Optional<Company> existing = companyService.getByBizKey(bizKey);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Company not found: " + bizKey));
            }

            Company company = existing.get();
            if (writeRouter.shouldWriteLocally(company.getCategory(), company.getOwnerRegion())) {
                Company updated = companyService.updateLocal(bizKey, name, address, industry);
                return ResponseEntity.ok(updated);
            } else {
                log.info("Forwarding Company UPDATE to owner region for bizKey={}", bizKey);
                var item = writeRouter.forwardCompanyUpdate(
                        company.getCategory(), company.getOwnerRegion(), bizKey, request);
                return ResponseEntity.ok(item);
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除 Company（软删除）。
     * DELETE /api/company/{bizKey}
     */
    @DeleteMapping("/{bizKey}")
    public ResponseEntity<?> delete(@PathVariable String bizKey) {
        try {
            Optional<Company> existing = companyService.getByBizKey(bizKey);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Company not found: " + bizKey));
            }

            Company company = existing.get();
            if (writeRouter.shouldWriteLocally(company.getCategory(), company.getOwnerRegion())) {
                companyService.deleteLocal(bizKey);
                return ResponseEntity.ok(Map.of("message", "Deleted company: " + bizKey));
            } else {
                log.info("Forwarding Company DELETE to owner region for bizKey={}", bizKey);
                writeRouter.forwardCompanyDelete(company.getCategory(), company.getOwnerRegion(), bizKey);
                return ResponseEntity.ok(Map.of("message", "Deleted (forwarded): " + bizKey));
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting company", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查看同步事件日志。
     * GET /api/company/sync-logs
     * GET /api/company/sync-logs?status=APPLIED
     * GET /api/company/sync-logs?sourceRegion=NA
     */
    @GetMapping("/sync-logs")
    public ResponseEntity<List<SyncEventLog>> getSyncLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceRegion) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(syncEventLogRepository.findByStatusOrderByCreatedAtDesc(status));
        }
        if (sourceRegion != null && !sourceRegion.isBlank()) {
            return ResponseEntity.ok(syncEventLogRepository.findBySourceRegionOrderByCreatedAtDesc(sourceRegion));
        }
        return ResponseEntity.ok(syncEventLogRepository.findAll());
    }
}
