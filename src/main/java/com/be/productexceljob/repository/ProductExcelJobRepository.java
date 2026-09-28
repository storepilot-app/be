package com.be.productexceljob.repository;

import com.be.productexceljob.domain.ProductExcelJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductExcelJobRepository extends JpaRepository<ProductExcelJob, Long> {
    Optional<ProductExcelJob> findByJobIdAndUserId(long jobId, Long userId);
}
