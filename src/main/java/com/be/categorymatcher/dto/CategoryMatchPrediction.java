package com.be.categorymatcher.dto;

import java.util.List;

public record CategoryMatchPrediction(
        int rowId,
        Long categoryId,
        String categoryCode,
        String fullPath,
        double score,
        List<CategoryMatchCandidate> candidates,
        Boolean llmUsed,
        String llmSelectedCategory,
        String llmStatus,
        String llmStatusDetail
) {
}
