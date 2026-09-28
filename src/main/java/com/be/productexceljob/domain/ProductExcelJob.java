package com.be.productexceljob.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Getter
@Entity
@Table(name = "product_excel_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductExcelJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobId;
    private Long userId;
    private String originalFilename;
    @Column(length = 2048)
    private String uploadedFilePath;
    private boolean includeSelectionDetails;
    private int productCount;
    private LocalDate usageDate;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private volatile ProductExcelJobStatus status;
    private volatile int totalCount;
    private volatile int processedCount;
    private volatile int progress;
    private volatile String stage;
    @Column(columnDefinition = "text")
    private volatile String message;
    private volatile Long categoryElapsedMillis;
    private volatile Long keywordElapsedMillis;
    private volatile String resultFilename;
    @Column(length = 2048)
    private String resultFilePath;
    private Instant completedAt;
    private Instant resultExpiresAt;
    private Instant resultDeletedAt;
    // 이전 버전에서 DB에 저장한 결과의 다운로드와 만료 처리를 위한 호환 필드.
    @Lob
    @Column(columnDefinition = "longblob")
    private volatile byte[] resultContent;

    private ProductExcelJob(
            Long userId,
            String originalFilename,
            Path uploadedFilePath,
            boolean includeSelectionDetails,
            int productCount,
            LocalDate usageDate
    ) {
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.uploadedFilePath = uploadedFilePath.toString();
        this.includeSelectionDetails = includeSelectionDetails;
        this.productCount = productCount;
        this.usageDate = usageDate;
        this.createdAt = Instant.now();
        this.status = ProductExcelJobStatus.PENDING;
        this.totalCount = productCount;
        this.stage = "작업 대기 중";
        this.message = "카테고리 찾기 작업이 등록되었습니다.";
    }

    public static ProductExcelJob register(
            Long userId,
            String originalFilename,
            Path uploadedFilePath,
            boolean includeSelectionDetails,
            int productCount,
            LocalDate usageDate
    ) {
        return new ProductExcelJob(
                userId,
                originalFilename,
                uploadedFilePath,
                includeSelectionDetails,
                productCount,
                usageDate
        );
    }

    public Path getUploadedFilePath() {
        return Path.of(uploadedFilePath);
    }

    public synchronized void markProcessing() {
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

    public synchronized void markCompleted(String resultFilename, String resultFilePath, Instant completedAt) {
        this.resultFilename = resultFilename;
        this.resultFilePath = resultFilePath;
        this.resultContent = null;
        this.completedAt = completedAt;
        this.resultExpiresAt = completedAt.plus(Duration.ofHours(24));
        this.processedCount = totalCount;
        this.progress = 100;
        this.stage = "완료";
        this.status = ProductExcelJobStatus.COMPLETED;
        this.message = "결과 엑셀을 다운로드할 수 있습니다.";
    }

    public boolean isResultExpired(Instant now) {
        return resultDeletedAt != null || (resultExpiresAt != null && !now.isBefore(resultExpiresAt));
    }

    public synchronized void recordCategoryElapsed(long elapsedMillis) {
        this.categoryElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void recordKeywordElapsed(long elapsedMillis) {
        this.keywordElapsedMillis = Math.max(0L, elapsedMillis);
    }

    public synchronized void markFailed(String message) {
        this.resultFilePath = null;
        this.resultContent = null;
        this.completedAt = null;
        this.resultExpiresAt = null;
        this.status = ProductExcelJobStatus.FAILED;
        this.stage = "실패";
        this.message = message;
    }

}
