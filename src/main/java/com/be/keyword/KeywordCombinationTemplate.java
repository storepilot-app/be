package com.be.keyword;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KeywordCombinationTemplate {
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 20;

    public List<String> generate(List<String> productTokens, List<String> categoryTokens) {
        return generate(productTokens, categoryTokens, List.of(), List.of());
    }

    public List<String> generate(
            List<String> productTokens,
            List<String> categoryTokens,
            List<String> repeatedPhrases
    ) {
        return generate(productTokens, categoryTokens, repeatedPhrases, List.of());
    }

    public List<String> generate(
            List<String> productTokens,
            List<String> categoryTokens,
            List<String> repeatedPhrases,
            List<String> synonyms
    ) {
        Map<String, String> candidates = new LinkedHashMap<>();
        List<String> safeProductTokens = productTokens == null ? List.of() : productTokens;
        List<String> safeCategoryTokens = categoryTokens == null ? List.of() : categoryTokens;
        List<String> safeRepeatedPhrases = repeatedPhrases == null ? List.of() : repeatedPhrases;
        List<String> safeSynonyms = synonyms == null ? List.of() : synonyms;

        safeCategoryTokens.forEach(token -> add(candidates, token));
        safeRepeatedPhrases.forEach(phrase -> add(candidates, phrase));
        safeSynonyms.forEach(synonym -> add(candidates, synonym));

        String primaryCategory = safeCategoryTokens.isEmpty() ? "" : safeCategoryTokens.get(0);
        if (!primaryCategory.isBlank()) {
            for (String productToken : safeProductTokens) {
                add(candidates, combineWithCategory(List.of(productToken), primaryCategory));
            }
            for (int index = 0; index + 1 < safeProductTokens.size(); index++) {
                add(candidates, combineWithCategory(
                        safeProductTokens.subList(index, index + 2),
                        primaryCategory
                ));
            }
        }

        add(candidates, String.join("", safeProductTokens));

        for (int index = 0; index + 1 < safeProductTokens.size(); index++) {
            add(candidates, safeProductTokens.get(index) + safeProductTokens.get(index + 1));
        }
        safeProductTokens.forEach(token -> add(candidates, token));

        return List.copyOf(candidates.values());
    }

    private String combineWithCategory(List<String> tokens, String primaryCategory) {
        StringBuilder prefix = new StringBuilder();
        for (String token : tokens) {
            if (!overlaps(token, primaryCategory)) {
                prefix.append(token);
            }
        }
        if (prefix.isEmpty()) {
            return primaryCategory;
        }
        return prefix + primaryCategory;
    }

    private boolean overlaps(String token, String category) {
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        String normalizedCategory = category.toLowerCase(Locale.ROOT);
        return normalizedCategory.contains(normalizedToken) || normalizedToken.contains(normalizedCategory);
    }

    private void add(Map<String, String> candidates, String value) {
        if (value == null) {
            return;
        }
        String keyword = value.replaceAll("\\s+", "").trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH || keyword.length() > MAX_KEYWORD_LENGTH) {
            return;
        }
        candidates.putIfAbsent(keyword.toLowerCase(Locale.ROOT), keyword);
    }
}
