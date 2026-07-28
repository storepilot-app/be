package com.be.qna.dto;

import com.be.qna.domain.QnaFaq;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FAQ 항목")
public record QnaFaqResponse(
        @Schema(description = "FAQ ID", example = "1")
        Long id,
        @Schema(description = "질문", example = "카테고리 및 키워드 찾기는 어떻게 사용하나요?")
        String question,
        @Schema(description = "답변")
        String answer
) {
    public static QnaFaqResponse from(QnaFaq faq) {
        return new QnaFaqResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
    }
}
