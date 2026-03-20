package com.example.regionsync.repository;

import com.example.regionsync.entity.SyncEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SyncEventLog 数据访问层。
 */
@Repository
public interface SyncEventLogRepository extends JpaRepository<SyncEventLog, String> {

    List<SyncEventLog> findBySourceRegionOrderByCreatedAtDesc(String sourceRegion);

    List<SyncEventLog> findByStatusOrderByCreatedAtDesc(String status);

    List<SyncEventLog> findByEntityIdOrderByCreatedAtDesc(String entityId);

    List<SyncEventLog> findBySequenceNumberGreaterThanOrderBySequenceNumberAsc(long afterSequence);

    List<SyncEventLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    long countByStatus(String status);
}
