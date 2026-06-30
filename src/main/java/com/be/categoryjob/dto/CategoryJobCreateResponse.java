package com.be.categoryjob.dto;

import com.be.categoryjob.domain.CategoryJobStatus;

public record CategoryJobCreateResponse(
        long jobId,
        CategoryJobStatus status,
        String message
) {
}
