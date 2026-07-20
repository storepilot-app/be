package com.be.trainingproduct.repository;

import com.be.trainingproduct.domain.ProductCategoryFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryFeedbackRepository extends JpaRepository<ProductCategoryFeedback, Long> {
    void deleteByUserId(Long userId);
}
