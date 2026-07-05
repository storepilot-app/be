package com.be.navercategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "네이버 카테고리 업로드 결과")
public record NaverCategoryUploadResponse(
        @Schema(description = "카테고리 버전 ID", example = "1")
        long versionId,
        @Schema(description = "업로드한 원본 파일명", example = "naver_categories.xlsx")
        String sourceFilename,
        @Schema(description = "엑셀에서 해석한 행 수", example = "5009")
        int rowCount,
        @Schema(description = "활성화된 카테고리 수", example = "5009")
        int categoryCount,
        @Schema(description = "생성된 CSV 캐시 경로", example = "uploads/naver-categories/active/naver_categories.csv")
        String csvPath,
        @Schema(description = "처리 결과 메시지", example = "네이버 카테고리가 업로드되었습니다.")
        String message
) {
}
