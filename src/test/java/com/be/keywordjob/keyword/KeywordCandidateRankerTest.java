package com.be.keywordjob.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordCandidateRankerTest {
    private final KeywordCandidateRanker ranker = new KeywordCandidateRanker();

    @Test
    void filtersGenericNumericRepeatedAndUnrelatedCandidates() {
        List<String> result = ranker.rank(
                        List.of("추천", "123", "키보드키보드", "노트북", "무선키보드"),
                        List.of("로지텍", "무선", "키보드"),
                        List.of("무선키보드", "키보드"),
                        List.of(),
                        List.of()
                ).stream()
                .map(KeywordCandidateRanker.ScoredKeyword::keyword)
                .toList();

        assertEquals(List.of("무선키보드"), result);
    }

    @Test
    void repeatedEvidenceRaisesOtherwiseEquivalentCandidate() {
        List<KeywordCandidateRanker.ScoredKeyword> result = ranker.rank(
                List.of("사무용키보드", "저소음키보드"),
                List.of("사무용", "저소음", "키보드"),
                List.of("키보드"),
                List.of("저소음키보드"),
                List.of()
        );

        assertEquals("저소음키보드", result.getFirst().keyword());
        assertTrue(result.getFirst().evidenceScore() > result.getLast().evidenceScore());
    }

    @Test
    void synonymEvidenceIsScoredAndAllScoresStayNormalized() {
        List<KeywordCandidateRanker.ScoredKeyword> result = ranker.rank(
                List.of("휴대폰케이스"),
                List.of("스마트폰", "케이스"),
                List.of("스마트폰케이스"),
                List.of(),
                List.of("휴대폰케이스")
        );

        KeywordCandidateRanker.ScoredKeyword keyword = result.getFirst();
        assertEquals(0.8, keyword.evidenceScore());
        assertFalse(result.isEmpty());
        assertTrue(keyword.finalScore() >= 0.0 && keyword.finalScore() <= 1.0);
        assertTrue(keyword.titleScore() >= 0.0 && keyword.titleScore() <= 1.0);
        assertTrue(keyword.categoryScore() >= 0.0 && keyword.categoryScore() <= 1.0);
    }
}
