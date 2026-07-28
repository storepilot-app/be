package com.be.qna.dto;

import com.be.qna.domain.QnaQuestion;
import com.be.qna.domain.QnaQuestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "1:1 문의 항목")
public record QnaQuestionResponse(
        @Schema(description = "문의 ID", example = "1")
        Long id,
        @Schema(description = "작성자 사용자 ID", example = "3")
        Long userId,
        @Schema(description = "문의 제목", example = "이미지 다운로드가 실패합니다.")
        String title,
        @Schema(description = "문의 내용")
        String content,
        @Schema(description = "문의 상태", example = "WAITING")
        QnaQuestionStatus status,
        @Schema(description = "관리자 답변")
        String answer,
        @Schema(description = "답변 관리자 ID", example = "1")
        Long answeredBy,
        @Schema(description = "답변 일시")
        Instant answeredAt,
        @Schema(description = "등록 일시")
        Instant createdAt,
        @Schema(description = "수정 일시")
        Instant updatedAt
) {
    public static QnaQuestionResponse from(QnaQuestion question) {
        return new QnaQuestionResponse(
                question.getId(),
                question.getUserId(),
                question.getTitle(),
                question.getContent(),
                question.getStatus(),
                question.getAnswer(),
                question.getAnsweredBy(),
                question.getAnsweredAt(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }
}
