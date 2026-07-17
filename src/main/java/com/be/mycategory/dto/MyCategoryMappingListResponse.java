package com.be.mycategory.dto;

import com.be.mycategory.domain.MyCategoryMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "마이카테고리 매핑 목록")
public record MyCategoryMappingListResponse(
        @Schema(description = "매핑 수", example = "120")
        int mappingCount,
        @Schema(description = "매핑 목록")
        List<MyCategoryMappingItemResponse> mappings
) {
    public static MyCategoryMappingListResponse from(List<MyCategoryMapping> mappings) {
        return new MyCategoryMappingListResponse(
                mappings.size(),
                mappings.stream()
                        .map(MyCategoryMappingItemResponse::from)
                        .toList()
        );
    }
}
