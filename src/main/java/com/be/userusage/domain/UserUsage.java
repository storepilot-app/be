package com.be.userusage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "user_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_usages_user_date",
                columnNames = {"user_id", "usage_date"}
        ),
        indexes = @Index(name = "idx_user_usages_usage_date", columnList = "usage_date")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "category_keyword_job_count", nullable = false)
    private long categoryKeywordJobCount;

    @Column(name = "processed_product_count", nullable = false)
    private long processedProductCount;

    @Column(name = "image_download_count", nullable = false)
    private long imageDownloadCount;

    @Column(name = "category_learning_request_count", nullable = false)
    private long categoryLearningRequestCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UserUsage(Long userId, LocalDate usageDate, Instant createdAt) {
        this.userId = userId;
        this.usageDate = usageDate;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static UserUsage create(Long userId, LocalDate usageDate) {
        return new UserUsage(userId, usageDate, Instant.now());
    }

    public void recordCategoryKeywordJob(long productCount) {
        validateCount(productCount);
        categoryKeywordJobCount++;
        processedProductCount += productCount;
        updatedAt = Instant.now();
    }

    public void recordImageDownloads(long imageCount) {
        validateCount(imageCount);
        imageDownloadCount += imageCount;
        updatedAt = Instant.now();
    }

    public void recordCategoryLearningRequest() {
        categoryLearningRequestCount++;
        updatedAt = Instant.now();
    }

    private void validateCount(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("사용량은 음수일 수 없습니다.");
        }
    }
}
