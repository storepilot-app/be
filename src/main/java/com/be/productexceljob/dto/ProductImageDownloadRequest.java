package com.be.productexceljob.dto;

public record ProductImageDownloadRequest(
        String url,
        Integer targetSizePercent,
        Boolean applyWatermark
) {
}
