package com.be.productexceljob.dto;

import java.time.Instant;

public record ProductExcelJobResultResponse(
        long jobId,
        String filename,
        int productCount,
        Instant completedAt,
        Instant resultExpiresAt,
        boolean resultExpired
) {
}
