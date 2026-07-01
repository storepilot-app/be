package com.be.keywordjob.service;

@FunctionalInterface
public interface CategoryJobProgressListener {
    CategoryJobProgressListener NO_OP = (processedCount, totalCount, stage) -> {
    };

    void onProgress(int processedCount, int totalCount, String stage);

    default void onCategoryCompleted(long elapsedMillis) {
    }

    default void onKeywordCompleted(long elapsedMillis) {
    }
}
