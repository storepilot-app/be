package com.be.keywordjob.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이미지 다운로드 결과")
public record ImageDownloadResponse(
        @Schema(description = "저장된 이미지 수", example = "300")
        int savedCount,
        @Schema(description = "건너뛰었거나 실패한 이미지 수", example = "0")
        int failedCount,
        @Schema(description = "이미지 저장 디렉터리")
        String imageOutputDir,
        @Schema(description = "처리 결과 메시지")
        String message
) {
}
