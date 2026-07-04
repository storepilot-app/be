package com.be.trainingproduct.dto;

public record ProductFeedbackAiResponse(
        String userKey,
        int indexedProductCount,
        String message
) {
}
