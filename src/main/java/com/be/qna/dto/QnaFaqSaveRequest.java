package com.be.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FAQ 저장 요청")
public record QnaFaqSaveRequest(
        @Schema(description = "FAQ 질문", example = "카테고리 및 키워드 찾기는 어떻게 사용하나요?")
        String question,
        @Schema(description = "FAQ 답변")
        String answer,
        @Schema(description = "정렬 순서", example = "1")
        Integer sortOrder
) {
}
