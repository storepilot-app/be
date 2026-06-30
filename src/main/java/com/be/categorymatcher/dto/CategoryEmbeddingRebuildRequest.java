package com.be.categorymatcher.dto;

import java.util.List;

public record CategoryEmbeddingRebuildRequest(
        Long versionId,
        List<CategoryEmbeddingItem> categories
) {
}
