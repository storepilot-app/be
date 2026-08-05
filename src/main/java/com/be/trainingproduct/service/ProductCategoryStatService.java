package com.be.trainingproduct.service;

import com.be.mycategory.domain.MyCategoryMapping;
import com.be.trainingproduct.domain.ProductCategoryStat;
import com.be.trainingproduct.dto.ProductCategoryStatsResponse;
import com.be.trainingproduct.repository.ProductCategoryStatRepository;
import java.time.Instant;
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

    @Transactional
    public void increaseStat(Long userId, MyCategoryMapping mapping) {
        Instant updatedAt = Instant.now();
        productCategoryStatRepository
                .findFirstByUserIdAndNaverCategoryCode(userId, mapping.getNaverCategoryCode())
                .ifPresentOrElse(
                        stat -> stat.increaseProductCount(updatedAt),
                        () -> productCategoryStatRepository.save(ProductCategoryStat.create(
                                userId,
                                mapping.getNaverCategoryId(),
                                mapping.getNaverCategoryCode(),
                                mapping.getNaverCategoryFullPath(),
                                1,
                                updatedAt
                        ))
                );
    }

    @Transactional
    public void moveStat(Long userId, String previousNaverCategoryCode, MyCategoryMapping nextMapping) {
        if (previousNaverCategoryCode == null
                || previousNaverCategoryCode.isBlank()
                || previousNaverCategoryCode.equals(nextMapping.getNaverCategoryCode())) {
            return;
        }

        Instant updatedAt = Instant.now();
        productCategoryStatRepository
                .findFirstByUserIdAndNaverCategoryCode(userId, previousNaverCategoryCode)
                .ifPresent(stat -> stat.decreaseProductCount(updatedAt));
        increaseStat(userId, nextMapping);
    }
}
