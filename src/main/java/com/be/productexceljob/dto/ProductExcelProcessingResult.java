package com.be.productexceljob.dto;

public record ProductExcelProcessingResult(
        ExcelDownloadResult userResult,
        ExcelDownloadResult adminResult
) {
}
