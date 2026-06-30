package com.be.categorymatcher.dto;

public record CategoryMatchMappingItem(
        String myCategoryCode,
        Long categoryId,
        String categoryCode,
        String fullPath
) {
}
