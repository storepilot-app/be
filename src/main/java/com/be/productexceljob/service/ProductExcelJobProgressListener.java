package com.be.productexceljob.service;

public interface ProductExcelJobProgressListener {
    void onProgress(int processedCount, int totalCount, String stage);

    default void onCategoryCompleted(long elapsedMillis) {
    }

    default void onKeywordCompleted(long elapsedMillis) {
    }
}
