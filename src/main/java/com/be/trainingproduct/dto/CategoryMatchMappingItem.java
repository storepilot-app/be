package com.be.trainingproduct.dto;

import com.be.mycategory.domain.MyCategoryMapping;

public record CategoryMatchMappingItem(
        String myCategoryCode,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
    public static CategoryMatchMappingItem from(MyCategoryMapping mapping) {
        return new CategoryMatchMappingItem(
                mapping.getMyCategoryCode(),
                mapping.getNaverCategoryId(),
                mapping.getNaverCategoryCode(),
                mapping.getNaverCategoryFullPath()
        );
    }
}
