package com.be.categorymatcher.dto;

import java.util.List;

public record CategoryMatchPredictRequest(
        Long versionId,
        String userKey,
        List<CategoryMatchProductRequest> products,
        List<CategoryMatchMappingItem> myCategoryMappings
) {
}
