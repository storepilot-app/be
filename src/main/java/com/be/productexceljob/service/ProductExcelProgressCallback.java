package com.be.productexceljob.service;

public interface ProductExcelProgressCallback {
    void onProgress(int processedCount, int totalCount, String stage);

    default void onCategoryCompleted(long elapsedMillis) {
    }

    default void onKeywordCompleted(long elapsedMillis) {
    }
}
