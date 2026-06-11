package com.be.keywordjob.domain;

import java.nio.file.Path;
import java.time.Instant;

public class KeywordJob {
    private final long jobId;
    private final String originalFilename;
    private final String productNameColumn;
    private final String categoryColumn;
    private final int keywordCount;
    private final Path uploadedFilePath;
    private final Instant createdAt;
    private KeywordJobStatus status;

    public KeywordJob(
            long jobId,
            String originalFilename,
            String productNameColumn,
            String categoryColumn,
            int keywordCount,
            Path uploadedFilePath
    ) {
        this.jobId = jobId;
        this.originalFilename = originalFilename;
        this.productNameColumn = productNameColumn;
        this.categoryColumn = categoryColumn;
        this.keywordCount = keywordCount;
        this.uploadedFilePath = uploadedFilePath;
        this.createdAt = Instant.now();
        this.status = KeywordJobStatus.PENDING;
    }

    public long getJobId() {
        return jobId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getProductNameColumn() {
        return productNameColumn;
    }

    public String getCategoryColumn() {
        return categoryColumn;
    }

    public int getKeywordCount() {
        return keywordCount;
    }

    public Path getUploadedFilePath() {
        return uploadedFilePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public KeywordJobStatus getStatus() {
        return status;
    }

    public void setStatus(KeywordJobStatus status) {
        this.status = status;
    }
}
