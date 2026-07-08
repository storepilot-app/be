package com.be.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTokenExtractorTest {
    private final CategoryTokenExtractor extractor = new CategoryTokenExtractor();

    @Test
    void extractsLeafCategoryBeforeParentCategories() {
        assertEquals(
                List.of("무선키보드", "키보드", "주변기기"),
                extractor.extract("디지털/가전 > 주변기기 > 키보드 > 무선키보드")
        );
    }

    @Test
    void splitsAliasesAndKeepsOnlyTheNearestThreeLevels() {
        assertEquals(
                List.of("클립", "핀", "문구용품", "문구", "사무용품"),
                extractor.extract("생활/건강 > 문구/사무용품 > 문구용품 > 클립/핀")
        );
    }

    @Test
    void removesGenericCategoriesAndDuplicates() {
        assertEquals(
                List.of("피규어", "모형", "프라모델"),
                extractor.extract("생활/건강 > 모형/프라모델/피규어 > 기타 > 피규어")
        );
    }
}
