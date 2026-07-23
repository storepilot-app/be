package com.be.productexceljob.dto;

import java.util.List;

public record ProductImageDownloadPrepareResponse(
        int imageCount,
        int failedCount,
        List<ProductImageDownloadItem> images,
        List<ProductImageDownloadFailure> failures
) {
}
