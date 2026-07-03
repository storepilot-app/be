package com.be.categoryjob.domain;

import java.nio.file.Path;
import java.time.Instant;

public class CategoryJob {
    private final long jobId;
    private final String userKey;
    private final String originalFilename;
    private final Path uploadedFilePath;
    private final Instant createdAt;
    private volatile CategoryJobStatus status;
    private volatile int totalCount;
    private volatile int processedCount;
    private volatile int progress;
    private volatile String stage;
    private volatile String message;
    private volatile Long categoryElapsedMillis;
    private volatile Long keywordElapsedMillis;
    private volatile String resultFilename;
    private volatile byte[] resultContent;

    public CategoryJob(long jobId, String userKey, String originalFilename, Path uploadedFilePath) {
        this.jobId = jobId;
        this.userKey = userKey;
        this.originalFilename = originalFilename;
        this.uploadedFilePath = uploadedFilePath;
        this.createdAt = Instant.now();
        this.status = CategoryJobStatus.PENDING;
        this.stage = "작업 대기 중";
        this.message = "카테고리 찾기 작업이 등록되었습니다.";
    }

    public synchronized void start() {
        status = CategoryJobStatus.PROCESSING;
        stage = "엑셀 분석 중";
        message = "카테고리 찾기 작업을 처리하고 있습니다.";
    }

    public synchronized void updateProgress(int processedCount, int totalCount, String stage) {
        this.totalCount = Math.max(totalCount, 0);
        this.processedCount = Math.max(0, Math.min(processedCount, this.totalCount));
        this.stage = stage;
        if (this.totalCount == 0) {
            this.progress = stage.contains("결과") ? 95 : 5;
        } else {
            this.progress = Math.min(95, (int) Math.round(this.processedCount * 95.0 / this.totalCount));
        }
    }

    public synchronized void complete(String resultFilename, byte[] resultContent) {
        this.resultFilename = resultFilename;
        this.resultContent = resultContent;
        this.processedCount = totalCount;
        this.progress = 100;
        this.stage = "완료";
        this.status = CategoryJobStatus.COMPLETED;
        this.message = "결과 엑셀을 다운로드할 수 있습니다.";
    }

    public synchronized void recordCategoryElapsed(long elapsedMillis) {
        this.categoryElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void recordKeywordElapsed(long elapsedMillis) {
        this.keywordElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void fail(String message) {
        this.status = CategoryJobStatus.FAILED;
        this.stage = "실패";
        this.message = message;
    }

    public long getJobId() {
        return jobId;
    }

    public String getUserKey() {
        return userKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public Path getUploadedFilePath() {
        return uploadedFilePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CategoryJobStatus getStatus() {
        return status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getProgress() {
        return progress;
    }

    public String getStage() {
        return stage;
    }

    public String getMessage() {
        return message;
    }

    public Long getCategoryElapsedMillis() {
        return categoryElapsedMillis;
    }

    public Long getKeywordElapsedMillis() {
        return keywordElapsedMillis;
    }

    public String getResultFilename() {
        return resultFilename;
    }

    public byte[] getResultContent() {
        return resultContent;
    }
}
