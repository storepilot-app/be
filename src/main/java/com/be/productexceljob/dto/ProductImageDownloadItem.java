package com.be.productexceljob.dto;

public record ProductImageDownloadItem(
        int rowNumber,
        String name,
        String filename,
        String url
) {
}
