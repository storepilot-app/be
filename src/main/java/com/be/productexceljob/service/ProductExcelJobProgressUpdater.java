package com.be.productexceljob.service;

import com.be.productexceljob.domain.ProductExcelJob;

public class ProductExcelJobProgressUpdater {
    private final ProductExcelJob job;

    public ProductExcelJobProgressUpdater(ProductExcelJob job) {
        this.job = job;
    }

    public void update(int processedCount, int totalCount, String stage) {
        job.updateProgress(processedCount, totalCount, stage);
    }

    public void recordCategoryCompleted(long elapsedMillis) {
        job.recordCategoryElapsed(elapsedMillis);
    }

    public void recordKeywordCompleted(long elapsedMillis) {
        job.recordKeywordElapsed(elapsedMillis);
    }
}
