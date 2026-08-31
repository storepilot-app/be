package com.be.productimage.dto;

import java.util.List;

public record ProductImageFailureExcelRequest(
        List<ProductImageDownloadFailure> failures
) {
}
