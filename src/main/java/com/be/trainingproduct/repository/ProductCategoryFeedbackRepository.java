package com.be.trainingproduct.repository;

import com.be.trainingproduct.domain.ProductCategoryFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryFeedbackRepository extends JpaRepository<ProductCategoryFeedback, Long> {
    Optional<ProductCategoryFeedback> findFirstByUserIdAndNormalizedProductKeyOrderByCreatedAtDesc(
            Long userId,
            String normalizedProductKey
    );

    void deleteByUserId(Long userId);
}
