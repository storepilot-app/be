package com.be.productexceljob.repository;

import com.be.productexceljob.domain.ProductExcelJob;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductExcelJobRepository extends JpaRepository<ProductExcelJob, Long> {
    Optional<ProductExcelJob> findByJobIdAndUserId(long jobId, Long userId);

    @Query("""
            select j.jobId from ProductExcelJob j
            where j.status = com.be.productexceljob.domain.ProductExcelJobStatus.COMPLETED
              and j.resultExpiresAt <= :now and j.resultDeletedAt is null
            """)
    List<Long> findExpiredResultIds(@Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            update ProductExcelJob j set j.resultDeletedAt = :now,
                j.resultFilePath = null, j.resultContent = null
            where j.jobId = :jobId
              and j.status = com.be.productexceljob.domain.ProductExcelJobStatus.COMPLETED
              and j.resultExpiresAt <= :now and j.resultDeletedAt is null
            """)
    int markResultDeleted(@Param("jobId") long jobId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            update ProductExcelJob j set j.resultExpiresAt = :expiresAt
            where j.status = com.be.productexceljob.domain.ProductExcelJobStatus.COMPLETED
              and j.resultExpiresAt is null and j.resultContent is not null
            """)
    int initializeLegacyResultExpiry(@Param("expiresAt") Instant expiresAt);
}
