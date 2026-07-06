package com.be.trainingproduct.dto;

import com.be.trainingproduct.domain.ProductCategoryFeedback;

public record ProductFeedbackAiRequest(
        String userKey,
        String productName,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
    public static ProductFeedbackAiRequest from(ProductCategoryFeedback feedback) {
        return new ProductFeedbackAiRequest(
                feedback.getUserKey(),
                feedback.getProductName(),
                feedback.getNaverCategoryId(),
                feedback.getNaverCategoryCode(),
                feedback.getNaverCategoryFullPath()
        );
    }
}
