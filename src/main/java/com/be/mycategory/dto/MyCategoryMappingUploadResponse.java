package com.be.mycategory.dto;

import com.be.mycategory.domain.MyCategoryMappingVersion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이카테고리 매핑 업로드 결과")
public record MyCategoryMappingUploadResponse(
        @Schema(description = "매핑 버전 ID", example = "1")
        Long versionId,
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        @Schema(description = "업로드한 원본 파일명", example = "my_categories.xlsx")
        String sourceFilename,
        @Schema(description = "엑셀에서 해석한 행 수", example = "120")
        int rowCount,
        @Schema(description = "저장된 매핑 수", example = "120")
        int mappingCount,
        @Schema(description = "활성 네이버 카테고리와 일치한 매핑 수", example = "118")
        int matchedCount,
        @Schema(description = "처리 결과 메시지", example = "마이카테고리 매핑이 업로드되었습니다.")
        String message
) {
    public static MyCategoryMappingUploadResponse from(MyCategoryMappingVersion version) {
        return new MyCategoryMappingUploadResponse(
                version.getId(),
                version.getUserId(),
                version.getSourceFilename(),
                version.getRowCount(),
                version.getMappingCount(),
                version.getMatchedCount(),
                "마이카테고리 매핑이 업로드되었습니다."
        );
    }
}
