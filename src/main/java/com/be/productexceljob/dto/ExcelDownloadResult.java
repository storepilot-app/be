package com.be.productexceljob.dto;

public record ExcelDownloadResult(
        String filename,
        byte[] content
) {
}
