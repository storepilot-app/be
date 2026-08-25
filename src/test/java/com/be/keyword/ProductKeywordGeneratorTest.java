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
