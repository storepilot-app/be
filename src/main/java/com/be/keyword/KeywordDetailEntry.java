package com.be.keyword;

import com.be.keyword.KeywordCandidateRanker.ScoredKeyword;
import java.util.List;

public record KeywordDetailEntry(
        int sourceRow,
        String productName,
        String naverCategory,
        int rank,
        ScoredKeyword scoredKeyword,
        List<String> reasons
) {
    public KeywordDetailEntry {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
