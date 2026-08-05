package com.be.trainingproduct.repository;

import com.be.trainingproduct.domain.ProductCategoryFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryFeedbackRepository extends JpaRepository<ProductCategoryFeedback, Long> {
    Optional<ProductCategoryFeedback> findFirstByUserIdAndNormalizedProductNameOrderByCreatedAtDesc(
            Long userId,
            String normalizedProductName
    );

    void deleteByUserId(Long userId);
}
