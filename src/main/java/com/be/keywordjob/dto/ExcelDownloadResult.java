package com.be.keywordjob.dto;

public record ExcelDownloadResult(
        String filename,
        byte[] content
) {
}
