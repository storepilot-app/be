package com.be.categorymatcher.dto;

public record ProductIndexRebuildResponse(
        String userKey,
        int sourceCount,
        int sourceRowCount,
        int validRowCount,
        int unmappedRowCount,
        int indexedProductCount,
        int duplicateRowCount,
        int conflictingTitleCount,
        String message
) {
}
