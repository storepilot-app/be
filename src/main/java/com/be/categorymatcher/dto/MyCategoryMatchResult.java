package com.be.categorymatcher.dto;

import java.util.List;

public record MyCategoryMatchResult(
        MyCategoryMatchStatus status,
        String myCategoryCode,
        String naverCategory,
        List<CategoryMatchCandidate> topNaverCategoryCandidates,
        String llmSelectedCategory,
        String llmStatus,
        String llmStatusDetail,
        List<CategoryMatchSimilarProduct> similarProducts
) {
    public MyCategoryMatchResult {
        topNaverCategoryCandidates = topNaverCategoryCandidates == null ? List.of() : List.copyOf(topNaverCategoryCandidates);
        similarProducts = similarProducts == null ? List.of() : List.copyOf(similarProducts);
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
        return matched(myCategoryCode, naverCategory, topNaverCategoryCandidates, llmSelectedCategory, null);
    }

    public static MyCategoryMatchResult matched(
            String myCategoryCode,
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
        String llmStatus
    ) {
        return matched(myCategoryCode, naverCategory, topNaverCategoryCandidates, llmSelectedCategory, llmStatus, null);
    }

    public static MyCategoryMatchResult matched(
            String myCategoryCode,
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
            String llmStatus,
            String llmStatusDetail
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.MATCHED, myCategoryCode, naverCategory, topNaverCategoryCandidates, llmSelectedCategory, llmStatus, llmStatusDetail, List.of());
    }

    public static MyCategoryMatchResult noCategoryMatch() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null, List.of(), null, null, null, List.of());
    }

    public static MyCategoryMatchResult noCategoryMatch(List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return noCategoryMatch(topNaverCategoryCandidates, null);
    }

    public static MyCategoryMatchResult noCategoryMatch(
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory
    ) {
        return noCategoryMatch(topNaverCategoryCandidates, llmSelectedCategory, null);
    }

    public static MyCategoryMatchResult noCategoryMatch(
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
            String llmStatus
    ) {
        return noCategoryMatch(topNaverCategoryCandidates, llmSelectedCategory, llmStatus, null);
    }

    public static MyCategoryMatchResult noCategoryMatch(
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
            String llmStatus,
            String llmStatusDetail
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null, topNaverCategoryCandidates, llmSelectedCategory, llmStatus, llmStatusDetail, List.of());
    }

    public static MyCategoryMatchResult noMyCategoryMapping(String naverCategory, List<CategoryMatchCandidate> topNaverCategoryCandidates) {
        return noMyCategoryMapping(naverCategory, topNaverCategoryCandidates, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory
    ) {
        return noMyCategoryMapping(naverCategory, topNaverCategoryCandidates, llmSelectedCategory, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
            String llmStatus
    ) {
        return noMyCategoryMapping(naverCategory, topNaverCategoryCandidates, llmSelectedCategory, llmStatus, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(
            String naverCategory,
            List<CategoryMatchCandidate> topNaverCategoryCandidates,
            String llmSelectedCategory,
            String llmStatus,
            String llmStatusDetail
    ) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING, null, naverCategory, topNaverCategoryCandidates, llmSelectedCategory, llmStatus, llmStatusDetail, List.of());
    }

    public MyCategoryMatchResult withSimilarProducts(List<CategoryMatchSimilarProduct> values) {
        return new MyCategoryMatchResult(
                status,
                myCategoryCode,
                naverCategory,
                topNaverCategoryCandidates,
                llmSelectedCategory,
                llmStatus,
                llmStatusDetail,
                values
        );
    }

    public List<String> topNaverCategories() {
        return topNaverCategoryCandidates.stream()
                .map(CategoryMatchCandidate::fullPath)
                .filter(fullPath -> fullPath != null && !fullPath.isBlank())
                .toList();
    }
}
