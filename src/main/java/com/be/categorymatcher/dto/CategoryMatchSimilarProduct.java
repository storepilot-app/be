package com.be.categorymatcher.dto;

public record CategoryMatchSimilarProduct(
        String productName,
        Long categoryId,
        String categoryCode,
        String fullPath,
        double similarity
) {
}
