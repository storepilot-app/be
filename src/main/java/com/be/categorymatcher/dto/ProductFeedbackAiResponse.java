package com.be.categorymatcher.dto;

public record ProductFeedbackAiResponse(
        String userKey,
        int indexedProductCount,
        String message
) {
}
