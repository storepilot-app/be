package com.be.trainingproduct.dto;

public record CategoryMatchMappingItem(
        String myCategoryCode,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
}
