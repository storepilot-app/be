package com.be.mycategory.dto;

import com.be.mycategory.domain.MyCategoryMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이카테고리 매핑 항목")
public record MyCategoryMappingItemResponse(
        @Schema(description = "매핑 ID", example = "1")
        Long id,
        @Schema(description = "마이카테고리 코드", example = "A001")
        String myCategoryCode,
        @Schema(description = "업로드 파일의 네이버 카테고리 값", example = "50008131")
        String naverCategoryValue,
        @Schema(description = "네이버 카테고리 ID", example = "4131")
        Long naverCategoryId,
        @Schema(description = "네이버 카테고리 코드", example = "50008131")
        String naverCategoryCode,
        @Schema(description = "네이버 카테고리 전체 경로", example = "생활/건강 > 문구/사무용품 > 카드/엽서/봉투 > 편지지")
        String naverCategoryFullPath
) {
    public static MyCategoryMappingItemResponse from(MyCategoryMapping mapping) {
        return new MyCategoryMappingItemResponse(
                mapping.getId(),
                mapping.getMyCategoryCode(),
                mapping.getNaverCategoryValue(),
                mapping.getNaverCategoryId(),
                mapping.getNaverCategoryCode(),
                mapping.getNaverCategoryFullPath()
        );
    }
}
