package com.be.productexceljob.dto;

import java.time.Instant;

public record AdminProductExcelJobResultResponse(
        long jobId,
        Long userId,
        String userEmail,
        String originalFilename,
        String filename,
        int productCount,
        Instant completedAt,
        Instant resultExpiresAt,
        boolean resultExpired
) {
}
