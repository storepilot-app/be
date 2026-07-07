package com.be.productexceljob.dto;

import com.be.productexceljob.domain.ProductExcelJobStatus;

public record ProductExcelJobStatusResponse(
        long jobId,
        ProductExcelJobStatus status,
        int totalCount,
        int processedCount,
        int progress,
        String stage,
        String message,
        Long categoryElapsedMillis,
        Long keywordElapsedMillis
) {
}
