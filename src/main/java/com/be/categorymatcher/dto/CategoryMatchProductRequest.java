package com.be.categorymatcher.dto;

public record CategoryMatchProductRequest(
        int rowId,
        String productName,
        String imageUrl
) {
    public CategoryMatchProductRequest(int rowId, String productName) {
        this(rowId, productName, null);
    }
}
