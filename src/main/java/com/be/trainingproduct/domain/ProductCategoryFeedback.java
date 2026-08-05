package com.be.trainingproduct.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_category_feedback",
        indexes = {
                @Index(name = "idx_product_feedback_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_product_feedback_my_category", columnList = "user_id, my_category_code"),
                @Index(name = "idx_product_feedback_user_normalized_key", columnList = "user_id, normalized_product_key")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_name", nullable = false, length = 1000)
    private String productName;

    @Column(name = "normalized_product_name", length = 1000)
    private String normalizedProductName;

    @Column(name = "normalized_product_key", length = 64)
    private String normalizedProductKey;

    @Column(name = "my_category_code", nullable = false, length = 100)
    private String myCategoryCode;

    @Column(name = "naver_category_id", nullable = false)
    private Long naverCategoryId;

    @Column(name = "naver_category_code", nullable = false, length = 30)
    private String naverCategoryCode;

    @Column(name = "naver_category_full_path", nullable = false, length = 500)
    private String naverCategoryFullPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ProductCategoryFeedback(
            Long userId,
            String productName,
            String normalizedProductName,
            String normalizedProductKey,
            String myCategoryCode,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            Instant createdAt
    ) {
        this.userId = userId;
        this.productName = productName;
        this.normalizedProductName = normalizedProductName;
        this.normalizedProductKey = normalizedProductKey;
        this.myCategoryCode = myCategoryCode;
        this.naverCategoryId = naverCategoryId;
        this.naverCategoryCode = naverCategoryCode;
        this.naverCategoryFullPath = naverCategoryFullPath;
        this.createdAt = createdAt;
    }

    public static ProductCategoryFeedback create(
            Long userId,
            String productName,
            String normalizedProductName,
            String normalizedProductKey,
            String myCategoryCode,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            Instant createdAt
    ) {
        return new ProductCategoryFeedback(
                userId,
                productName,
                normalizedProductName,
                normalizedProductKey,
                myCategoryCode,
                naverCategoryId,
                naverCategoryCode,
                naverCategoryFullPath,
                createdAt
        );
    }
}
