package com.be.productexceljob.dto;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import java.time.Instant;

public record ProductExcelJobStatusResponse(
        long jobId,
        ProductExcelJobStatus status,
        int totalCount,
        int processedCount,
        int progress,
        String stage,
        String message,
        Long categoryElapsedMillis,
        Long keywordElapsedMillis,
        Instant resultExpiresAt,
        boolean resultExpired
) {
    public static ProductExcelJobStatusResponse from(ProductExcelJob job) {
        return new ProductExcelJobStatusResponse(
                job.getJobId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getProcessedCount(),
                job.getProgress(),
                job.getStage(),
                job.isResultExpired(Instant.now())
                        ? "결과 파일의 보관 기간이 만료되었습니다. 다시 작업해 주세요." : job.getMessage(),
                job.getCategoryElapsedMillis(),
                job.getKeywordElapsedMillis(),
                job.getResultExpiresAt(),
                job.isResultExpired(Instant.now())
        );
    }
}
