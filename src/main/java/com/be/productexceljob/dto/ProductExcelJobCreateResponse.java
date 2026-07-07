package com.be.productexceljob.dto;

import com.be.productexceljob.domain.ProductExcelJobStatus;

public record ProductExcelJobCreateResponse(
        long jobId,
        ProductExcelJobStatus status,
        String message
) {
}
