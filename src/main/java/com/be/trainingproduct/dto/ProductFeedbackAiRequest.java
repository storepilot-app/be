package com.be.trainingproduct.dto;

import com.be.trainingproduct.domain.ProductCategoryFeedback;

public record ProductFeedbackAiRequest(
        Long userId,
        String productName,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
    public static ProductFeedbackAiRequest from(ProductCategoryFeedback feedback) {
        return new ProductFeedbackAiRequest(
                feedback.getUserId(),
                feedback.getProductName(),
                feedback.getNaverCategoryId(),
                feedback.getNaverCategoryCode(),
                feedback.getNaverCategoryFullPath()
        );
    }
}
