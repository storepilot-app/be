package com.be.productimage.dto;

public record ProductImageDownloadRequest(
        String url,
        Integer targetSizePercent,
        Boolean applyWatermark
) {
}
