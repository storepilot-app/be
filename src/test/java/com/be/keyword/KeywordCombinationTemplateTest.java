package com.be.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordCombinationTemplateTest {
    private final KeywordCombinationTemplate template = new KeywordCombinationTemplate();

    @Test
    void combinesProductTokensWithPrimaryCategoryWithoutRepeatedWords() {
        List<String> result = template.generate(
                List.of("로지텍", "K380", "블루투스", "무선", "키보드"),
                List.of("무선키보드", "키보드", "주변기기")
        );

        assertEquals(List.of("무선키보드", "키보드", "주변기기"), result.subList(0, 3));
        assertTrue(result.contains("로지텍무선키보드"));
        assertTrue(result.contains("K380무선키보드"));
        assertTrue(result.contains("블루투스무선키보드"));
        assertTrue(result.contains("로지텍K380무선키보드"));
        assertTrue(result.contains("로지텍K380블루투스무선키보드"));
        assertFalse(result.contains("무선무선키보드"));
        assertFalse(result.contains("키보드무선키보드"));
    }

    @Test
    void generatesAdjacentCombinationsWithoutCategory() {
        List<String> result = template.generate(
                List.of("말랑고구마", "스틱"),
                List.of()
        );

        assertEquals(List.of("말랑고구마스틱", "말랑고구마", "스틱"), result);
    }

    @Test
    void removesDuplicatesAndCandidatesLongerThanTwentyCharacters() {
        List<String> result = template.generate(
                List.of("키보드", "키보드", "아주아주아주아주아주아주긴상품토큰"),
                List.of("키보드")
        );

        assertEquals(1, result.stream().filter("키보드"::equals).count());
        assertTrue(result.stream().noneMatch(keyword -> keyword.length() > 20));
    }

    @Test
    void placesRepeatedExpressionsBeforeGeneratedCombinations() {
        List<String> result = template.generate(
                List.of("로지텍", "블루투스", "키보드"),
                List.of("무선키보드"),
                List.of("블루투스키보드", "키보드")
        );

        assertEquals(List.of("무선키보드", "블루투스키보드", "키보드"), result.subList(0, 3));
    }

    @Test
    void includesSynonymsBeforeGeneratedCombinations() {
        List<String> result = template.generate(
                List.of("스마트폰", "케이스"),
                List.of("휴대폰케이스"),
                List.of(),
                List.of("핸드폰", "핸드폰케이스")
        );

        assertEquals(
                List.of("휴대폰케이스", "핸드폰", "핸드폰케이스"),
                result.subList(0, 3)
        );
    }
}
