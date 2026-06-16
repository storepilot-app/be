package com.be.mycategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "My category mapping upload response")
public record MyCategoryMappingUploadResponse(
        @Schema(description = "Mapping version ID", example = "1")
        Long versionId,
        @Schema(description = "User key", example = "user-a")
        String userKey,
        @Schema(description = "Source filename", example = "my_categories.xlsx")
        String sourceFilename,
        @Schema(description = "Parsed row count", example = "120")
        int rowCount,
        @Schema(description = "Saved mapping count", example = "120")
        int mappingCount,
        @Schema(description = "Rows matched to active Naver category data", example = "118")
        int matchedCount,
        @Schema(description = "Uploaded file path")
        String uploadedFilePath,
        @Schema(description = "Response message", example = "My category mappings uploaded.")
        String message
) {
}
