package com.be.navercategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Naver category upload response")
public record NaverCategoryUploadResponse(
        @Schema(description = "Category version ID", example = "1")
        long versionId,
        @Schema(description = "Uploaded source filename", example = "naver_categories.xlsx")
        String sourceFilename,
        @Schema(description = "Parsed row count", example = "5009")
        int rowCount,
        @Schema(description = "Active category count", example = "5009")
        int categoryCount,
        @Schema(description = "Generated CSV cache path", example = "uploads/naver-categories/active/naver_categories.csv")
        String csvPath,
        @Schema(description = "Response message", example = "Naver categories uploaded.")
        String message
) {
}
