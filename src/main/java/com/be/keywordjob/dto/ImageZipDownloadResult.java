package com.be.keywordjob.dto;

public record ImageZipDownloadResult(
        String filename,
        byte[] content,
        int savedCount,
        int failedCount
) {
}
