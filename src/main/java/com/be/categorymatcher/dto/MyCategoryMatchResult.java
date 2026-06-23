package com.be.categorymatcher.dto;

import java.util.List;

public record MyCategoryMatchResult(
        MyCategoryMatchStatus status,
        String myCategoryCode,
        String naverCategory,
        List<CategoryMatchCandidate> topNaverCategoryCandidates
) {
    public MyCategoryMatchResult {
        topNaverCategoryCandidates = topNaverCategoryCandidates == null ? List.of() : List.copyOf(topNaverCategoryCandidates);
    }

    public static MyCategoryMatchResult matched(String myCategoryCode, String naverCategory, List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.MATCHED, myCategoryCode, naverCategory, topNaverCategoryCandidates);
    }

    public static MyCategoryMatchResult noCategoryMatch() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null, List.of());
    }

    public static MyCategoryMatchResult noMyCategoryMapping(String naverCategory, List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING, null, naverCategory, topNaverCategoryCandidates);
    }

    public List<String> topNaverCategories() {
        return topNaverCategoryCandidates.stream()
                .map(CategoryMatchCandidate::fullPath)
                .filter(fullPath -> fullPath != null && !fullPath.isBlank())
                .toList();
    }
}
