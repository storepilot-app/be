package com.be.categorymatcher.dto;

import java.util.List;

public record MyCategoryMatchResult(
        MyCategoryMatchStatus status,
        String myCategoryCode,
        String naverCategory,
        List<CategoryMatchCandidate> topNaverCategoryCandidates,
        String llmSelectedCategory
) {
    public MyCategoryMatchResult {
        topNaverCategoryCandidates = topNaverCategoryCandidates == null ? List.of() : List.copyOf(topNaverCategoryCandidates);
    }

    public static MyCategoryMatchResult matched(String myCategoryCode, String naverCategory, List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return matched(myCategoryCode, naverCategory, topNaverCategoryCandidates, null);
    }

    public static MyCategoryMatchResult matched(
            String myCategoryCode,
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.MATCHED, myCategoryCode, naverCategory, topNaverCategoryCandidates, llmSelectedCategory);
    }

    public static MyCategoryMatchResult noCategoryMatch() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null, List.of(), null);
    }

    public static MyCategoryMatchResult noCategoryMatch(List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return noCategoryMatch(topNaverCategoryCandidates, null);
    }

    public static MyCategoryMatchResult noCategoryMatch(
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null, topNaverCategoryCandidates, llmSelectedCategory);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(String naverCategory, List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return noMyCategoryMapping(naverCategory, topNaverCategoryCandidates, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING, null, naverCategory, topNaverCategoryCandidates, llmSelectedCategory);
    }

    public List<String> topNaverCategories() {
        return topNaverCategoryCandidates.stream()
                .map(CategoryMatchCandidate::fullPath)
                .filter(fullPath -> fullPath != null && !fullPath.isBlank())
                .toList();
    }
}
