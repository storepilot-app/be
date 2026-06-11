package com.be.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "INVALID_EXCEL_FILE")
        ErrorCode errorCode,
        @Schema(description = "에러 메시지", example = "엑셀 파일 형식이 올바르지 않습니다.")
        String message
) {
}
