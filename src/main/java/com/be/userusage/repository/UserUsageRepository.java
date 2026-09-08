package com.be.userusage.repository;

import com.be.userusage.domain.UserUsage;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO user_usages (
                user_id,
                usage_date,
                category_keyword_job_count,
                processed_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 1, :productCount, 0, 0, :now, :now)
            ON DUPLICATE KEY UPDATE
                category_keyword_job_count = category_keyword_job_count + 1,
                processed_product_count = processed_product_count + :productCount,
                updated_at = :now
            """, nativeQuery = true)
    void upsertCategoryKeywordUsage(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("productCount") long productCount,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            INSERT INTO user_usages (
                user_id,
                usage_date,
                category_keyword_job_count,
                processed_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 0, 0, :imageCount, 0, :now, :now)
            ON DUPLICATE KEY UPDATE
                image_download_count = image_download_count + :imageCount,
                updated_at = :now
            """, nativeQuery = true)
    void upsertImageDownloadUsage(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("imageCount") long imageCount,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            INSERT INTO user_usages (
                user_id,
                usage_date,
                category_keyword_job_count,
                processed_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 0, 0, 0, 1, :now, :now)
            ON DUPLICATE KEY UPDATE
                category_learning_request_count = category_learning_request_count + 1,
                updated_at = :now
            """, nativeQuery = true)
    void upsertCategoryLearningRequestUsage(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("now") Instant now
    );
}
