package com.be.trainingproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User-confirmed product category")
public record ProductCategoryFeedbackRequest(
        @Schema(example = "uno1969") String userKey,
        @Schema(example = "카카오프렌즈 라이프이즈하드 전자 계산기") String productName,
        @Schema(example = "WB5047610") String myCategoryCode
) {
}
