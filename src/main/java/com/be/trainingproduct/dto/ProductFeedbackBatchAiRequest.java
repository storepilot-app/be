package com.be.trainingproduct.dto;

import java.util.List;

public record ProductFeedbackBatchAiRequest(
        Long userId,
        List<ProductFeedbackAiRequest> products
) {
}
