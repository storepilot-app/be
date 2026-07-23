package com.be.productexceljob.domain;

import java.nio.file.Path;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ProductExcelJob {
    private final long jobId;
    private final Long userId;
    private final String originalFilename;
    private final Path uploadedFilePath;
    private final Instant createdAt;
    private volatile ProductExcelJobStatus status;
    private volatile int totalCount;
    private volatile int processedCount;
    private volatile int progress;
    private volatile String stage;
    private volatile String message;
    private volatile Long categoryElapsedMillis;
    private volatile Long keywordElapsedMillis;
    private volatile String resultFilename;
    private volatile byte[] resultContent;

    private ProductExcelJob(long jobId, Long userId, String originalFilename, Path uploadedFilePath) {
        this.jobId = jobId;
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.uploadedFilePath = uploadedFilePath;
        this.createdAt = Instant.now();
        this.status = ProductExcelJobStatus.PENDING;
        this.stage = "작업 대기 중";
        this.message = "카테고리 찾기 작업이 등록되었습니다.";
    }

    public static ProductExcelJob register(long jobId, Long userId, String originalFilename, Path uploadedFilePath) {
        return new ProductExcelJob(jobId, userId, originalFilename, uploadedFilePath);
    }

    public synchronized void start() {
        status = ProductExcelJobStatus.PROCESSING;
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
        this.status = ProductExcelJobStatus.COMPLETED;
        this.message = "결과 엑셀을 다운로드할 수 있습니다.";
    }

    public synchronized void recordCategoryElapsed(long elapsedMillis) {
        this.categoryElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void recordKeywordElapsed(long elapsedMillis) {
        this.keywordElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void fail(String message) {
        this.status = ProductExcelJobStatus.FAILED;
        this.stage = "실패";
        this.message = message;
    }

}
