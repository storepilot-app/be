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
                @Index(name = "idx_product_feedback_user_created", columnList = "user_key, created_at"), //사용자별 피드백 이력을 최신순으로 조회할 때 유용
                @Index(name = "idx_product_feedback_my_category", columnList = "user_key, my_category_code") //사용자마다 번호 체계가 다르므로 user_key와 함께 조회
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 100)
    private String userKey;

    @Column(name = "product_name", nullable = false, length = 1000)
    private String productName;

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
            String userKey,
            String productName,
            String myCategoryCode,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            Instant createdAt
    ) {
        this.userKey = userKey;
        this.productName = productName;
        this.myCategoryCode = myCategoryCode;
        this.naverCategoryId = naverCategoryId;
        this.naverCategoryCode = naverCategoryCode;
        this.naverCategoryFullPath = naverCategoryFullPath;
        this.createdAt = createdAt;
    }

    public static ProductCategoryFeedback create(
            String userKey,
            String productName,
            String myCategoryCode,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath,
            Instant createdAt
    ) {
        return new ProductCategoryFeedback(
                userKey,
                productName,
                myCategoryCode,
                naverCategoryId,
                naverCategoryCode,
                naverCategoryFullPath,
                createdAt
        );
    }
}
