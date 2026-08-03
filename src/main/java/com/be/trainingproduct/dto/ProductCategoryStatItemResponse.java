package com.be.trainingproduct.dto;

import com.be.trainingproduct.domain.ProductCategoryStat;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProductCategoryStatItemResponse(
        @Schema(description = "네이버 카테고리 ID") Long naverCategoryId,
        @Schema(description = "네이버 카테고리 코드") String naverCategoryCode,
        @Schema(description = "네이버 카테고리 전체 경로") String naverCategoryFullPath,
        @Schema(description = "기존 상품 수") long productCount
) {
    public static ProductCategoryStatItemResponse from(ProductCategoryStat stat) {
        return new ProductCategoryStatItemResponse(
                stat.getNaverCategoryId(),
                stat.getNaverCategoryCode(),
                stat.getNaverCategoryFullPath(),
                stat.getProductCount()
        );
    }
}
