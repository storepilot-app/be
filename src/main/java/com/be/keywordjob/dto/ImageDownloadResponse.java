package com.be.keywordjob.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Image download response")
public record ImageDownloadResponse(
        @Schema(description = "Saved image count", example = "300")
        int savedCount,
        @Schema(description = "Skipped or failed image count", example = "0")
        int failedCount,
        @Schema(description = "Image output directory")
        String imageOutputDir,
        @Schema(description = "Response message")
        String message
) {
}
