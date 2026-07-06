package com.be.trainingproduct.dto;

import com.be.trainingproduct.domain.ProductCategoryFeedback;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 카테고리 수정 피드백 저장 결과")
public record ProductCategoryFeedbackResponse(
        @Schema(description = "피드백 ID") Long feedbackId,
        @Schema(description = "사용자 식별자") String userKey,
        @Schema(description = "마이카테고리 코드") String myCategoryCode,
        @Schema(description = "매핑된 네이버 카테고리 전체 경로") String naverCategory,
        @Schema(description = "현재 인덱싱된 전체 상품 수") int indexedProductCount,
        @Schema(description = "처리 결과 메시지") String message
) {
    public static ProductCategoryFeedbackResponse from(
            ProductCategoryFeedback feedback,
            ProductFeedbackAiResponse aiResponse
    ) {
        return new ProductCategoryFeedbackResponse(
                feedback.getId(),
                feedback.getUserKey(),
                feedback.getMyCategoryCode(),
                feedback.getNaverCategoryFullPath(),
                aiResponse.indexedProductCount(),
                "상품 카테고리 수정 피드백이 저장되었습니다."
        );
    }
}
