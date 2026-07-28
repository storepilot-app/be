package com.be.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "1:1 문의 답변 요청")
public record QnaQuestionAnswerRequest(
        @Schema(description = "관리자 답변", example = "확인 후 수정했습니다.")
        String answer
) {
}
