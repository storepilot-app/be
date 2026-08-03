package com.be.trainingproduct.dto;

import com.be.trainingproduct.domain.ProductCategoryStat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record ProductCategoryStatsResponse(
        @Schema(description = "집계된 네이버 카테고리 종류 수") int categoryCount,
        @Schema(description = "집계된 기존 상품 총 수") long totalProductCount,
        @Schema(description = "최근 집계 시각") Instant updatedAt,
        @Schema(description = "네이버 카테고리별 기존 상품 수") List<ProductCategoryStatItemResponse> stats
) {
    public static ProductCategoryStatsResponse from(List<ProductCategoryStat> stats) {
        long totalProductCount = stats.stream()
                .mapToLong(ProductCategoryStat::getProductCount)
                .sum();
        Instant updatedAt = stats.stream()
                .map(ProductCategoryStat::getUpdatedAt)
                .findFirst()
                .orElse(null);

        return new ProductCategoryStatsResponse(
                stats.size(),
                totalProductCount,
                updatedAt,
                stats.stream()
                        .map(ProductCategoryStatItemResponse::from)
                        .toList()
        );
    }
}
