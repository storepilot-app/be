package com.be.keywordjob.keyword;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KeywordCandidateRanker {
    private static final double TITLE_WEIGHT = 0.45;
    private static final double CATEGORY_WEIGHT = 0.30;
    private static final double EVIDENCE_WEIGHT = 0.15;
    private static final double SPECIFICITY_WEIGHT = 0.10;
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 20;
    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)?$");
    private static final Pattern VALID_CHARACTER_PATTERN = Pattern.compile("^[\\p{IsHangul}A-Za-z0-9+.-]+$");
    private static final Set<String> GENERIC_KEYWORDS = Set.of(
            "상품", "제품", "용품", "추천", "인기", "베스트", "신상품",
            "기타", "일반", "전체", "판매", "구매", "쇼핑", "선물"
    );

    public List<ScoredKeyword> rank(
            List<String> candidates,
            List<String> productTokens,
            List<String> categoryTokens,
            List<String> repeatedPhrases,
            List<String> synonyms
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, IndexedKeyword> uniqueCandidates = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            String keyword = clean(candidates.get(index));
            if (isStructurallyValid(keyword)) {
                uniqueCandidates.putIfAbsent(normalize(keyword), new IndexedKeyword(index, keyword));
            }
        }

        List<ScoredKeyword> scoredKeywords = new ArrayList<>();
        for (IndexedKeyword candidate : uniqueCandidates.values()) {
            double titleScore = relevance(candidate.keyword(), productTokens);
            double categoryScore = relevance(candidate.keyword(), categoryTokens);
            double evidenceScore = evidenceScore(candidate.keyword(), repeatedPhrases, synonyms);
            if (titleScore == 0.0 && categoryScore == 0.0 && evidenceScore == 0.0) {
                continue;
            }

            double specificityScore = specificityScore(candidate.keyword());
            double finalScore = TITLE_WEIGHT * titleScore
                    + CATEGORY_WEIGHT * categoryScore
                    + EVIDENCE_WEIGHT * evidenceScore
                    + SPECIFICITY_WEIGHT * specificityScore;
            scoredKeywords.add(new ScoredKeyword(
                    candidate.keyword(),
                    round(finalScore),
                    round(titleScore),
                    round(categoryScore),
                    round(evidenceScore),
                    round(specificityScore),
                    candidate.index()
            ));
        }

        return scoredKeywords.stream()
                .sorted(Comparator
                        .comparingDouble(ScoredKeyword::finalScore).reversed()
                        .thenComparingInt(ScoredKeyword::originalIndex))
                .toList();
    }

    private double relevance(String keyword, List<String> sourceTerms) {
        if (sourceTerms == null || sourceTerms.isEmpty()) {
            return 0.0;
        }

        String normalizedKeyword = normalize(keyword);
        List<String> normalizedTerms = sourceTerms.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(this::normalize)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        if (normalizedTerms.contains(normalizedKeyword)) {
            return 1.0;
        }

        int matchedCharacters = 0;
        List<String> matchedTerms = new ArrayList<>();
        double partialScore = 0.0;
        for (String term : normalizedTerms) {
            if (normalizedKeyword.contains(term)
                    && matchedTerms.stream().noneMatch(selected -> selected.contains(term))) {
                matchedTerms.add(term);
                matchedCharacters += term.length();
            } else if (term.contains(normalizedKeyword)) {
                partialScore = Math.max(
                        partialScore,
                        0.9 * normalizedKeyword.length() / (double) term.length()
                );
            }
        }

        double coverageScore = Math.min(1.0, matchedCharacters / (double) normalizedKeyword.length());
        String compactSource = String.join("", normalizedTerms);
        if (compactSource.contains(normalizedKeyword)) {
            coverageScore = Math.max(coverageScore, 0.95);
        }
        return Math.max(coverageScore, partialScore);
    }

    private double evidenceScore(String keyword, List<String> repeatedPhrases, List<String> synonyms) {
        double repeatedScore = overlapScore(keyword, repeatedPhrases, 1.0, 0.85);
        double synonymScore = overlapScore(keyword, synonyms, 0.8, 0.65);
        return Math.max(repeatedScore, synonymScore);
    }

    private double overlapScore(String keyword, List<String> evidence, double exactScore, double partialScore) {
        if (evidence == null) {
            return 0.0;
        }
        String normalizedKeyword = normalize(keyword);
        for (String value : evidence) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalizedEvidence = normalize(value);
            if (normalizedKeyword.equals(normalizedEvidence)) {
                return exactScore;
            }
            if (normalizedKeyword.contains(normalizedEvidence) || normalizedEvidence.contains(normalizedKeyword)) {
                return partialScore;
            }
        }
        return 0.0;
    }

    private double specificityScore(String keyword) {
        int length = normalize(keyword).length();
        if (length >= 4 && length <= 15) {
            return 1.0;
        }
        if (length <= 3) {
            return 0.65;
        }
        return 0.55;
    }

    private boolean isStructurallyValid(String keyword) {
        if (keyword.length() < MIN_KEYWORD_LENGTH || keyword.length() > MAX_KEYWORD_LENGTH) {
            return false;
        }
        String normalized = normalize(keyword);
        if (GENERIC_KEYWORDS.contains(normalized) || PURE_NUMBER_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (!VALID_CHARACTER_PATTERN.matcher(keyword).matches()) {
            return false;
        }
        return !isRepeatedExpression(normalized);
    }

    private boolean isRepeatedExpression(String keyword) {
        if (keyword.length() < 6 || keyword.length() % 2 != 0) {
            return false;
        }
        int midpoint = keyword.length() / 2;
        return keyword.substring(0, midpoint).equals(keyword.substring(midpoint));
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record IndexedKeyword(int index, String keyword) {
    }

    public record ScoredKeyword(
            String keyword,
            double finalScore,
            double titleScore,
            double categoryScore,
            double evidenceScore,
            double specificityScore,
            int originalIndex
    ) {
    }
}
