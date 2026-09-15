package com.be.keyword;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.be.productexceljob.service.ProductKeywordGenerator;
import com.be.productexceljob.service.ProductKeywordGenerator.GeneratedKeyword;
import com.be.productexceljob.service.ProductKeywordGenerator.ProductKeywordSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductKeywordGeneratorTest {
    @Test
    void usesImageEvidenceOnlyWhenConsistentWithProductName() {
        var image = new com.be.categorymatcher.dto.ImageProductAnalysis(
                "반창고", List.of("파란색"), List.of(), List.of("방수"), List.of(), true, 0.9);
        var result = generator.generate(List.of(new ProductKeywordSource(1, "캐릭터 상품", "", image)), 30);
        assertTrue(result.get(1).stream().anyMatch(keyword -> keyword.score().keyword().contains("반창고")));
        assertTrue(result.get(1).stream().anyMatch(keyword -> keyword.reasons().contains("이미지 분석 보조 정보")));
        assertFalse(result.get(1).stream().anyMatch(keyword -> keyword.score().keyword().contains("방수")));
        var mismatch = new com.be.categorymatcher.dto.ImageProductAnalysis(
                "반창고", List.of(), List.of(), List.of(), List.of(), false, 0.99);
        var textOnly = generator.generate(List.of(new ProductKeywordSource(1, "캐릭터 상품", "")), 30);
        org.junit.jupiter.api.Assertions.assertEquals(textOnly,
                generator.generate(List.of(new ProductKeywordSource(1, "캐릭터 상품", "", mismatch)), 30));
    }
    private final ProductNameTokenExtractor productNameTokenExtractor = new ProductNameTokenExtractor();
    private final ProductKeywordGenerator generator = new ProductKeywordGenerator(
            new CategoryTokenExtractor(),
            new KeywordCandidateRanker(),
            new KeywordCombinationTemplate(),
            new KeywordSynonymDictionary(List.of("스마트폰=휴대폰")),
            productNameTokenExtractor,
            new SimilarProductRepeatedPhraseExtractor(productNameTokenExtractor)
    );

    @Test
    void generatesRankedKeywordsWithinRequestedCount() {
        Map<Integer, List<GeneratedKeyword>> result = generator.generate(
                List.of(new ProductKeywordSource(
                        1,
                        "로지텍 저소음 블루투스 키보드",
                        "디지털 > 키보드"
                )),
                3
        );

        List<GeneratedKeyword> keywords = result.get(1);
        assertFalse(keywords.isEmpty());
        assertTrue(keywords.size() <= 3);
        assertTrue(keywords.stream().allMatch(keyword -> !keyword.reasons().isEmpty()));
    }

    @Test
    void includesRepeatedPhraseEvidenceFromProductsInSameCategory() {
        Map<Integer, List<GeneratedKeyword>> result = generator.generate(
                List.of(
                        new ProductKeywordSource(1, "로지텍 저소음 블루투스 키보드", "디지털 > 키보드"),
                        new ProductKeywordSource(2, "휴대용 블루투스 키보드", "디지털 > 키보드"),
                        new ProductKeywordSource(3, "사무용 저소음 키보드", "디지털 > 키보드")
                ),
                30
        );

        assertTrue(result.get(1).stream()
                .flatMap(keyword -> keyword.reasons().stream())
                .anyMatch("유사상품 반복 표현"::equals));
    }
}
