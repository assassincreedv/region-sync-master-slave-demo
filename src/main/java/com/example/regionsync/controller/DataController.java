package com.example.regionsync.controller;

import com.example.regionsync.model.DataItem;
import com.example.regionsync.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for data CRUD operations.
 *
 * Write endpoints (POST, PUT, DELETE) are only available on MASTER nodes.
 * Read endpoints (GET) are available on both MASTER and SLAVE nodes.
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);

    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Create a new data item.
     * Only allowed on MASTER nodes.
     *
     * POST /api/data
     * Body: { "key": "myKey", "value": "myValue" }
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        try {
            String key = request.get("key");
            String value = request.get("value");

            if (key == null || key.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "key is required"));
            }
            if (value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
            }

            DataItem item = dataService.create(key, value);
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a data item by key.
     * Available on both MASTER and SLAVE nodes.
     *
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
     * Get all data items.
     * Available on both MASTER and SLAVE nodes.
     *
     * GET /api/data
     */
    @GetMapping
    public ResponseEntity<List<DataItem>> getAll() {
        return ResponseEntity.ok(dataService.getAll());
    }

    /**
     * Update a data item.
     * Only allowed on MASTER nodes.
     *
     * PUT /api/data/{key}
     * Body: { "value": "newValue" }
     */
    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key, @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            if (value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
            }

            DataItem item = dataService.update(key, value);
            return ResponseEntity.ok(item);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a data item.
     * Only allowed on MASTER nodes.
     *
     * DELETE /api/data/{key}
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<?> delete(@PathVariable String key) {
        try {
            dataService.delete(key);
            return ResponseEntity.ok(Map.of("message", "Deleted: " + key));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
