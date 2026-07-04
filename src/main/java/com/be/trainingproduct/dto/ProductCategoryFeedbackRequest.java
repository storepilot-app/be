package com.be.trainingproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자가 확정한 상품 카테고리 정보")
public record ProductCategoryFeedbackRequest(
        @Schema(description = "사용자 식별자", example = "uno1969") String userKey,
        @Schema(description = "상품명", example = "카카오프렌즈 라이언 계산기") String productName,
        @Schema(description = "사용자가 확정한 마이카테고리 코드", example = "WB5047610") String myCategoryCode
) {
}
