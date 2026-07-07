package com.be.navercategory.dto;

public record CategoryEmbeddingItem(
        Long categoryId,
        String categoryCode,
        String fullPath,
        String searchText
) {
}
