package com.be.productexceljob.service;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.repository.ProductExcelJobRepository;

public class ProductExcelJobProgressUpdater {
    private final ProductExcelJob job;
    private final ProductExcelJobRepository repository;

    public ProductExcelJobProgressUpdater(ProductExcelJob job, ProductExcelJobRepository repository) {
        this.job = job;
        this.repository = repository;
    }

    public void update(int processedCount, int totalCount, String stage) {
        job.updateProgress(processedCount, totalCount, stage);
        repository.save(job);
    }

    public void recordCategoryCompleted(long elapsedMillis) {
        job.recordCategoryElapsed(elapsedMillis);
        repository.save(job);
    }

    public void recordKeywordCompleted(long elapsedMillis) {
        job.recordKeywordElapsed(elapsedMillis);
        repository.save(job);
    }
}
