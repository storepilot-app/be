package com.be.navercategory.dto;

import java.util.List;

public record CategoryEmbeddingRebuildRequest(
        Long versionId,
        List<CategoryEmbeddingItem> categories
) {
}
