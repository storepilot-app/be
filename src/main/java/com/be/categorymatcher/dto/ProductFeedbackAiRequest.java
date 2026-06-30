package com.be.categorymatcher.dto;

public record ProductFeedbackAiRequest(
        String userKey,
        String productName,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
}
