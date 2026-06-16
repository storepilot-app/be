package com.be.categorymatcher.dto;

public record CategoryMatchPrediction(
        int rowId,
        Long categoryId,
        String categoryCode,
        String fullPath,
        double score
) {
}
