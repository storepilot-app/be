package com.be.keywordjob.dto;

import com.be.keywordjob.domain.KeywordJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "키워드 생성 작업 등록 응답")
public record KeywordJobUploadResponse(
        @Schema(description = "작업 ID", example = "1")
        long jobId,
        @Schema(description = "작업 상태", example = "PENDING")
        KeywordJobStatus status,
        @Schema(description = "응답 메시지", example = "키워드 생성 작업이 등록되었습니다.")
        String message
) {
}
