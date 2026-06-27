package com.be.categorymatcher.dto;

public record CategoryMatchSimilarProduct(
        String productName,
        String myCategoryCode,
        Long categoryId,
        String categoryCode,
        String fullPath,
        double similarity
) {
}
