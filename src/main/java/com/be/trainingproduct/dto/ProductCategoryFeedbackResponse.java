package com.be.trainingproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Saved category feedback result")
public record ProductCategoryFeedbackResponse(
        Long feedbackId,
        String userKey,
        String myCategoryCode,
        String naverCategory,
        int indexedProductCount,
        String message
) {
}
