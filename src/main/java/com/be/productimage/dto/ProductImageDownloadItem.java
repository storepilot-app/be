package com.be.productimage.dto;

public record ProductImageDownloadItem(
        int rowNumber,
        String name,
        String filename,
        String url
) {
}
