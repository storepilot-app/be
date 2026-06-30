package com.be.categorymatcher.dto;

import java.util.List;

public record CategoryMatchPredictRequest(
        Long versionId,
        List<CategoryMatchProductRequest> products
) {
}
