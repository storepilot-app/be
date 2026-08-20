package com.be.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자주 묻는 질문 저장 요청")
public record QnaFaqSaveRequest(
        @Schema(description = "질문", example = "카테고리 및 키워드 찾기는 어떻게 사용하나요?")
        String question,
        @Schema(description = "답변")
        String answer,
        @Schema(description = "정렬 순서", example = "1")
        Integer sortOrder
) {
}
