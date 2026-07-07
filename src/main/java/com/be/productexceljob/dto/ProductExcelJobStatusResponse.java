package com.be.productexceljob.dto;

import com.be.productexceljob.domain.ProductExcelJob;
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
    public static ProductExcelJobStatusResponse from(ProductExcelJob job) {
        return new ProductExcelJobStatusResponse(
                job.getJobId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getProcessedCount(),
                job.getProgress(),
                job.getStage(),
                job.getMessage(),
                job.getCategoryElapsedMillis(),
                job.getKeywordElapsedMillis()
        );
    }
}
