package com.be.trainingproduct.service;

import com.be.trainingproduct.domain.ProductCategoryStat;
import com.be.trainingproduct.dto.ProductCategoryStatsResponse;
import com.be.trainingproduct.repository.ProductCategoryStatRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCategoryStatService {
    private final ProductCategoryStatRepository productCategoryStatRepository;

    @Transactional(readOnly = true)
    public ProductCategoryStatsResponse getStats(Long userId) {
        return ProductCategoryStatsResponse.from(
                productCategoryStatRepository.findByUserIdOrderByProductCountDescNaverCategoryFullPathAsc(userId)
        );
    }

    @Transactional
    public void replaceStats(Long userId, List<ProductCategoryStat> stats) {
        productCategoryStatRepository.deleteByUserId(userId);
        productCategoryStatRepository.saveAll(stats);
    }
}
