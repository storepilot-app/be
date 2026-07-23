package com.be.productexceljob.dto;

public record ProductImageDownloadFailure(
        int rowNumber,
        String name,
        String url,
        String reason
) {
}
