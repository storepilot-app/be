package com.be.keywordjob.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordSynonymDictionaryTest {

    @Test
    void expandsExactTermsInBothDirections() {
        KeywordSynonymDictionary dictionary = new KeywordSynonymDictionary(List.of(
                "스마트폰|휴대폰|핸드폰"
        ));

        assertEquals(List.of("휴대폰", "핸드폰"), dictionary.findSynonyms(List.of("스마트폰")));
        assertEquals(List.of("스마트폰", "핸드폰"), dictionary.findSynonyms(List.of("휴대폰")));
    }

    @Test
    void replacesSynonymsInsideCompoundTerms() {
        KeywordSynonymDictionary dictionary = new KeywordSynonymDictionary(List.of(
                "스마트폰|휴대폰|핸드폰"
        ));

        assertEquals(
                List.of("휴대폰케이스", "핸드폰케이스"),
                dictionary.findSynonyms(List.of("스마트폰케이스"))
        );
    }

    @Test
    void keepsTheSourceTermForDetailedReasons() {
        KeywordSynonymDictionary dictionary = new KeywordSynonymDictionary(List.of(
                "스마트폰|휴대폰"
        ));

        KeywordSynonymDictionary.SynonymExpansion expansion = dictionary
                .findExpansions(List.of("스마트폰케이스"))
                .getFirst();

        assertEquals("스마트폰케이스", expansion.sourceTerm());
        assertEquals("휴대폰케이스", expansion.keyword());
    }

    @Test
    void ignoresCommentsBlankLinesAndUnrelatedTerms() {
        KeywordSynonymDictionary dictionary = new KeywordSynonymDictionary(List.of(
                "# comment",
                "",
                "노트북|랩탑"
        ));

        assertEquals(List.of(), dictionary.findSynonyms(List.of("키보드")));
    }
}
