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
        String llmStatusDetail,
        List<CategoryMatchSimilarProduct> similarProducts,
        ImageProductAnalysis imageAnalysis,
        String imageAnalysisStatus
) {
    public CategoryMatchPrediction(int rowId, Long categoryId, String categoryCode, String fullPath,
            double score, List<CategoryMatchCandidate> candidates, Boolean llmUsed,
            String llmSelectedCategory, String llmStatus, String llmStatusDetail,
            List<CategoryMatchSimilarProduct> similarProducts) {
        this(rowId, categoryId, categoryCode, fullPath, score, candidates, llmUsed,
                llmSelectedCategory, llmStatus, llmStatusDetail, similarProducts, null, "SKIPPED");
    }
}
