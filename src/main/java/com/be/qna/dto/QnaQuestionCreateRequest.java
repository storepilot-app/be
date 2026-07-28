package com.be.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "1:1 문의 등록 요청")
public record QnaQuestionCreateRequest(
        @Schema(description = "문의 제목", example = "이미지 다운로드가 실패합니다.")
        String title,
        @Schema(description = "문의 내용", example = "상품 이미지 다운로드에서 일부 이미지가 저장되지 않습니다.")
        String content
) {
}
