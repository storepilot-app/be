package com.be.productimage.dto;

public record ProductImageDownloadFailure(
        int rowNumber,
        String name,
        String url,
        String reason
) {
}
