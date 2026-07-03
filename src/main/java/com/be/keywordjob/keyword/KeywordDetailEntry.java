package com.be.keywordjob.keyword;

import com.be.keywordjob.keyword.KeywordCandidateRanker.ScoredKeyword;
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
