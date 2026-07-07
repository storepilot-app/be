package com.be.productexceljob.dto;

public record ImageZipDownloadResult(
        String filename,
        byte[] content,
        int savedCount,
        int failedCount
) {
}
