package com.example.regionsync.controller;

import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.DataItem;
import com.example.regionsync.service.DataService;
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
 * 数据 CRUD REST 控制器。
 *
 * <p>写操作路由逻辑：
 * <ul>
 *   <li>如果本节点是该数据的 Owner → 本地写入</li>
 *   <li>如果本节点不是 Owner → 通过 {@link RegionWriteRouter} 转发到 Owner Region</li>
 * </ul>
 *
 * <p>读操作：所有 Region 都可以读本地副本（数据已通过同步到达）。
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);

    private final DataService dataService;
    private final RegionWriteRouter writeRouter;

    public DataController(DataService dataService, RegionWriteRouter writeRouter) {
        this.dataService = dataService;
        this.writeRouter = writeRouter;
    }

    /**
     * 创建数据。
     *
     * POST /api/data
     * Body: { "key": "na-job-001", "value": "Software Engineer", "category": "JOB", "ownerRegion": "NA" }
     *
     * 如果 ownerRegion 不是本节点，会自动转发到 owner region。
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        try {
            String key = request.get("key");
            String value = request.get("value");
            String categoryStr = request.get("category");
            String ownerRegion = request.get("ownerRegion");

            if (key == null || key.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "key is required"));
            }
            if (value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
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

            // 如果未指定 ownerRegion，默认为本节点（适用于 REGIONAL 类型）
            if (ownerRegion == null || ownerRegion.isBlank()) {
                ownerRegion = writeRouter.shouldWriteLocally(category, null)
                        ? request.getOrDefault("ownerRegion", "") : "";
            }

            // 判断是本地写还是转发
            if (writeRouter.shouldWriteLocally(category, ownerRegion)) {
                DataItem item = dataService.createLocal(key, value, category, ownerRegion);
                return ResponseEntity.status(HttpStatus.CREATED).body(item);
            } else {
                log.info("Forwarding CREATE to owner region for category={}, ownerRegion={}",
                        category, ownerRegion);
                DataItem item = writeRouter.forwardCreate(category, ownerRegion, request);
                return ResponseEntity.status(HttpStatus.CREATED).body(item);
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按 key 读取数据（所有 Region 均可读本地副本）。
     * GET /api/data/{key}
     */
    @GetMapping("/{key}")
    public ResponseEntity<?> get(@PathVariable String key) {
        Optional<DataItem> item = dataService.get(key);
        if (item.isPresent()) {
            return ResponseEntity.ok(item.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Data item not found: " + key));
    }

    /**
     * 获取所有数据。
     * GET /api/data
     * GET /api/data?category=JOB
     */
    @GetMapping
    public ResponseEntity<List<DataItem>> getAll(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            try {
                DataCategory cat = DataCategory.valueOf(category.toUpperCase());
                return ResponseEntity.ok(dataService.getAllByCategory(cat));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(dataService.getAll());
            }
        }
        return ResponseEntity.ok(dataService.getAll());
    }

    /**
     * 更新数据。
     * PUT /api/data/{key}
     * Body: { "value": "newValue" }
     *
     * 如果本节点不是该数据的 Owner，会自动转发。
     */
    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key, @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            if (value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
            }

            // 先查出数据，判断归属
            Optional<DataItem> existing = dataService.get(key);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Data item not found: " + key));
            }

            DataItem item = existing.get();
            if (writeRouter.shouldWriteLocally(item.getCategory(), item.getOwnerRegion())) {
                DataItem updated = dataService.updateLocal(key, value);
                return ResponseEntity.ok(updated);
            } else {
                log.info("Forwarding UPDATE to owner region for key={}", key);
                DataItem updated = writeRouter.forwardUpdate(
                        item.getCategory(), item.getOwnerRegion(), key, request);
                return ResponseEntity.ok(updated);
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除数据。
     * DELETE /api/data/{key}
     *
     * 如果本节点不是该数据的 Owner，会自动转发。
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<?> delete(@PathVariable String key) {
        try {
            Optional<DataItem> existing = dataService.get(key);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Data item not found: " + key));
            }

            DataItem item = existing.get();
            if (writeRouter.shouldWriteLocally(item.getCategory(), item.getOwnerRegion())) {
                dataService.deleteLocal(key);
                return ResponseEntity.ok(Map.of("message", "Deleted: " + key));
            } else {
                log.info("Forwarding DELETE to owner region for key={}", key);
                writeRouter.forwardDelete(item.getCategory(), item.getOwnerRegion(), key);
                return ResponseEntity.ok(Map.of("message", "Deleted (forwarded): " + key));
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
