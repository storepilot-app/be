package com.be.qna.dto;

import com.be.qna.domain.QnaQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "1:1 문의 목록 응답")
public record QnaQuestionListResponse(
        @Schema(description = "문의 개수", example = "3")
        int questionCount,
        @Schema(description = "문의 목록")
        List<QnaQuestionResponse> questions
) {
    public static QnaQuestionListResponse from(List<QnaQuestion> questions) {
        return new QnaQuestionListResponse(
                questions.size(),
                questions.stream()
                        .map(QnaQuestionResponse::from)
                        .toList()
        );
    }
}
