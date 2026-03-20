package com.example.regionsync.repository;

import com.example.regionsync.entity.Company;
import com.example.regionsync.model.DataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Company 数据访问层。
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {

    Optional<Company> findByBizKey(String bizKey);

    Optional<Company> findByBizKeyAndDeletedFalse(String bizKey);

    List<Company> findByDeletedFalse();

    List<Company> findByCategoryAndDeletedFalse(DataCategory category);

    List<Company> findByOwnerRegionAndDeletedFalse(String ownerRegion);

    List<Company> findByCategoryAndOwnerRegionAndDeletedFalse(DataCategory category, String ownerRegion);

    long countByDeletedFalse();
}
