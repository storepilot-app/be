package com.be.categoryjob.dto;

import com.be.categoryjob.domain.CategoryJobStatus;

public record CategoryJobStatusResponse(
        long jobId,
        CategoryJobStatus status,
        int totalCount,
        int processedCount,
        int progress,
        String stage,
        String message,
        Long categoryElapsedMillis,
        Long keywordElapsedMillis
) {
}
