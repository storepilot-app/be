package com.be.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.be.keyword.SimilarProductRepeatedPhraseExtractor.ProductSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimilarProductRepeatedPhraseExtractorTest {
    private final SimilarProductRepeatedPhraseExtractor extractor =
            new SimilarProductRepeatedPhraseExtractor(new ProductNameTokenExtractor());

    @Test
    void extractsExpressionsRepeatedInDifferentProductsOfSameCategory() {
        Map<Integer, List<String>> result = extractor.extract(List.of(
                new ProductSource(1, "로지텍 저소음 블루투스 키보드", "디지털 > 키보드"),
                new ProductSource(2, "휴대용 블루투스 키보드", "디지털 > 키보드"),
                new ProductSource(3, "사무용 저소음 키보드", "디지털 > 키보드")
        ));

        assertTrue(result.get(1).contains("블루투스키보드"));
        assertTrue(result.get(1).contains("저소음"));
        assertTrue(result.get(2).contains("블루투스키보드"));
        assertFalse(result.get(2).contains("저소음"));
        assertTrue(result.get(3).contains("저소음"));
    }

    @Test
    void doesNotMixExpressionsFromDifferentCategories() {
        Map<Integer, List<String>> result = extractor.extract(List.of(
                new ProductSource(1, "블루투스 키보드", "디지털 > 키보드"),
                new ProductSource(2, "블루투스 스피커", "디지털 > 스피커")
        ));

        assertEquals(List.of(), result.get(1));
        assertEquals(List.of(), result.get(2));
    }

    @Test
    void duplicateProductNamesDoNotCreateFalseRepetition() {
        Map<Integer, List<String>> result = extractor.extract(List.of(
                new ProductSource(1, "휴대용 블루투스 키보드", "디지털 > 키보드"),
                new ProductSource(2, "휴대용 블루투스 키보드", "디지털 > 키보드")
        ));

        assertEquals(List.of(), result.get(1));
        assertEquals(List.of(), result.get(2));
    }
}
