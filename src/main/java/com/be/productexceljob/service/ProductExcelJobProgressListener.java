package com.be.productexceljob.service;

@FunctionalInterface
public interface ProductExcelJobProgressListener {
    ProductExcelJobProgressListener NO_OP = (processedCount, totalCount, stage) -> {
    };

    void onProgress(int processedCount, int totalCount, String stage);

    default void onCategoryCompleted(long elapsedMillis) {
    }

    default void onKeywordCompleted(long elapsedMillis) {
    }
}
