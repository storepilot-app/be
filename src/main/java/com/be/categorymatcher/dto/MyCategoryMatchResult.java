package com.be.categorymatcher.dto;

public record MyCategoryMatchResult(
        MyCategoryMatchStatus status,
        String myCategoryCode
) {
    public static MyCategoryMatchResult matched(String myCategoryCode) {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.MATCHED, myCategoryCode);
    }

    public static MyCategoryMatchResult noCategoryMatch() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_CATEGORY_MATCH, null);
    }

    public static MyCategoryMatchResult noMyCategoryMapping() {
        return new MyCategoryMatchResult(MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING, null);
    }
}
