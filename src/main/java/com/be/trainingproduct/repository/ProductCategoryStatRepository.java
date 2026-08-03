package com.be.trainingproduct.repository;

import com.be.trainingproduct.domain.ProductCategoryStat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryStatRepository extends JpaRepository<ProductCategoryStat, Long> {
    List<ProductCategoryStat> findByUserIdOrderByProductCountDescNaverCategoryFullPathAsc(Long userId);

    void deleteByUserId(Long userId);
}
