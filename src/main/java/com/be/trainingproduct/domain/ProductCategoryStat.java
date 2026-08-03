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
        name = "product_category_stats",
        indexes = {
                @Index(name = "idx_product_category_stats_user", columnList = "user_id"),
                @Index(name = "idx_product_category_stats_user_count", columnList = "user_id, product_count")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "naver_category_id", nullable = false)
    private Long naverCategoryId;

    @Column(name = "naver_category_code", nullable = false, length = 30)
    private String naverCategoryCode;

    @Column(name = "naver_category_full_path", nullable = false, length = 500)
    private String naverCategoryFullPath;

    @Column(name = "product_count", nullable = false)
    private long productCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ProductCategoryStat(
            Long userId,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            long productCount,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.naverCategoryId = naverCategoryId;
        this.naverCategoryCode = naverCategoryCode;
        this.naverCategoryFullPath = naverCategoryFullPath;
        this.productCount = productCount;
        this.updatedAt = updatedAt;
    }

    public static ProductCategoryStat create(
            Long userId,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            long productCount,
            Instant updatedAt
    ) {
        return new ProductCategoryStat(
                userId,
                naverCategoryId,
                naverCategoryCode,
                naverCategoryFullPath,
                productCount,
                updatedAt
        );
    }
}
