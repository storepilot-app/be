package com.be.categorymatcher.dto;

public record ProductIndexRebuildResponse(
        String userKey,
        int sourceCount,
        int validRowCount,
        int indexedProductCount,
        int duplicateRowCount,
        int conflictingTitleCount,
        String message
) {
}
