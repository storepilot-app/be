package com.be.navercategory.domain;

public record NaverCategory(
        String categoryCode,
        String level1,
        String level2,
        String level3,
        String level4,
        String fullPath,
        String searchText
) {
}
