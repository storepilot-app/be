package com.be.categorymatcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category embedding rebuild response")
public record CategoryEmbeddingRebuildResponse(
        @Schema(description = "Active Naver category version ID", example = "1")
        Long versionId,
        @Schema(description = "Response message", example = "Category embeddings rebuild requested.")
        String message
) {
}
