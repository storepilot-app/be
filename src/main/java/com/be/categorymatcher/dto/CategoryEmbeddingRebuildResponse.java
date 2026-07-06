package com.be.categorymatcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 임베딩 재생성 요청 결과")
public record CategoryEmbeddingRebuildResponse(
        @Schema(description = "활성 네이버 카테고리 버전 ID", example = "1")
        Long versionId,
        @Schema(description = "처리 결과 메시지", example = "카테고리 임베딩 재생성을 요청했습니다.")
        String message
) {
    public static CategoryEmbeddingRebuildResponse requested(Long versionId) {
        return new CategoryEmbeddingRebuildResponse(
                versionId,
                "카테고리 임베딩 재생성을 요청했습니다."
        );
    }

    public static CategoryEmbeddingRebuildResponse noActiveVersion() {
        return new CategoryEmbeddingRebuildResponse(
                null,
                "활성화된 네이버 카테고리 버전이 없습니다."
        );
    }
}
