package com.be.categorymatcher.dto;

public record ProductFeedbackAiRequest(
        String userKey,
        String productName,
        String myCategoryCode
) {
}
