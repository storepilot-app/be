package com.be.productexceljob.dto;

import java.util.List;

public record ProductImageFailureExcelRequest(
        List<ProductImageDownloadFailure> failures
) {
}
