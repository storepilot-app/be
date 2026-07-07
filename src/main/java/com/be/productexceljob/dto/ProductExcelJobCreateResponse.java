package com.be.productexceljob.dto;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;

public record ProductExcelJobCreateResponse(
        long jobId,
        ProductExcelJobStatus status,
        String message
) {
    public static ProductExcelJobCreateResponse from(ProductExcelJob job) {
        return new ProductExcelJobCreateResponse(
                job.getJobId(),
                job.getStatus(),
                job.getMessage()
        );
    }
}
