package com.be.keywordjob.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductNameTokenExtractorTest {
    private final ProductNameTokenExtractor extractor = new ProductNameTokenExtractor();

    @Test
    void extractsBrandModelAttributesAndProductType() {
        assertEquals(
                List.of("로지텍", "K380", "블루투스", "무선", "키보드"),
                extractor.extract("[로지텍] K380 블루투스 무선 키보드 2개 무료배송")
        );
    }

    @Test
    void preservesHyphenatedModelNames() {
        assertEquals(
                List.of("소니", "WH-1000XM5", "노이즈캔슬링", "헤드폰"),
                extractor.extract("소니 WH-1000XM5 노이즈캔슬링 헤드폰")
        );
    }

    @Test
    void removesQuantitiesButKeepsUppercaseModelUnits() {
        assertEquals(
                List.of("30MM", "프라모델"),
                extractor.extract("30MM 프라모델 250g 250G 3개 2SET 1+1")
        );
    }

    @Test
    void normalizesSeparatorsAndRemovesDuplicateTokens() {
        assertEquals(
                List.of("무선", "키보드"),
                extractor.extract("무선/키보드, 무선（키보드）")
        );
    }
}
