package com.be.trainingproduct.dto;

public record ProductFeedbackAiResponse(
        Long userId,
        int indexedProductCount,
        String message
) {
}
