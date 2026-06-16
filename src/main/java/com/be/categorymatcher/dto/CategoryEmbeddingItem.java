package com.be.categorymatcher.dto;

public record CategoryEmbeddingItem(
        Long categoryId,
        String categoryCode,
        String fullPath,
        String searchText
) {
}
