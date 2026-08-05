package com.be.trainingproduct.repository;

import com.be.trainingproduct.domain.ProductCategoryStat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryStatRepository extends JpaRepository<ProductCategoryStat, Long> {
    List<ProductCategoryStat> findByUserIdOrderByProductCountDescNaverCategoryFullPathAsc(Long userId);

    Optional<ProductCategoryStat> findFirstByUserIdAndNaverCategoryCode(Long userId, String naverCategoryCode);

    void deleteByUserId(Long userId);
}
