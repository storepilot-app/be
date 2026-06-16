package com.be.categorymatcher.dto;

public record MyCategoryMatchResult(
        MyCategoryMatchStatus status,
        String myCategoryCode,
        String naverCategory
) {
    public static MyCategoryMatchResult matched(String myCategoryCode, String naverCategory) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.MATCHED, myCategoryCode, naverCategory);
    }

    public static MyCategoryMatchResult noCategoryMatch() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping(String naverCategory) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING, null, naverCategory);
    }
}
