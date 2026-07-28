package com.be.qna.dto;

import com.be.qna.domain.QnaFaq;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "FAQ 목록 응답")
public record QnaFaqListResponse(
        @Schema(description = "FAQ 개수", example = "5")
        int faqCount,
        @Schema(description = "FAQ 목록")
        List<QnaFaqResponse> faqs
) {
    public static QnaFaqListResponse from(List<QnaFaq> faqs) {
        return new QnaFaqListResponse(
                faqs.size(),
                faqs.stream()
                        .map(QnaFaqResponse::from)
                        .toList()
        );
    }
}
