package com.be.userusage.repository;

import com.be.userusage.domain.UserUsage;
import com.be.userusage.dto.AdminUserUsageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    List<UserUsage> findByUserIdAndUsageDateBetweenOrderByUsageDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            SELECT new com.be.userusage.dto.AdminUserUsageResponse(
                user.id,
                user.email,
                user.role,
                COALESCE(SUM(usage.categoryKeywordJobCount), 0),
                COALESCE(SUM(usage.processedProductCount), 0),
                COALESCE(SUM(usage.imageDownloadCount), 0),
                COALESCE(SUM(usage.categoryLearningRequestCount), 0),
                MAX(usage.usageDate)
            )
            FROM StorePilotUser user
            LEFT JOIN UserUsage usage
                ON usage.userId = user.id
                AND usage.usageDate BETWEEN :startDate AND :endDate
            GROUP BY user.id, user.email, user.role, user.createdAt
            ORDER BY COALESCE(SUM(usage.processedProductCount), 0) DESC, user.createdAt DESC
            """)
    List<AdminUserUsageResponse> findAdminUserUsages(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(value = """
            INSERT INTO user_usages (
                user_id,
                usage_date,
                category_keyword_job_count,
                processed_product_count,
                reserved_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 0, 0, 0, 0, 0, :now, :now)
            ON DUPLICATE KEY UPDATE updated_at = updated_at
            """, nativeQuery = true)
    void ensureUsageExists(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            UPDATE user_usages
            SET reserved_product_count = reserved_product_count + :productCount,
                updated_at = :now
            WHERE user_id = :userId
              AND usage_date = :usageDate
              AND processed_product_count + reserved_product_count + :productCount <= :dailyLimit
            """, nativeQuery = true)
    int reserveProductsWithinDailyLimit(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("productCount") long productCount,
            @Param("dailyLimit") long dailyLimit,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            UPDATE user_usages
            SET category_keyword_job_count = category_keyword_job_count + 1,
                processed_product_count = processed_product_count + :productCount,
                reserved_product_count = GREATEST(reserved_product_count - :productCount, 0),
                updated_at = :now
            WHERE user_id = :userId AND usage_date = :usageDate
            """, nativeQuery = true)
    void completeCategoryKeywordUsage(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("productCount") long productCount,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            UPDATE user_usages
            SET reserved_product_count = GREATEST(reserved_product_count - :productCount, 0),
                updated_at = :now
            WHERE user_id = :userId AND usage_date = :usageDate
            """, nativeQuery = true)
    void releaseReservedProducts(
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
                reserved_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 0, 0, 0, :imageCount, 0, :now, :now)
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
                reserved_product_count,
                image_download_count,
                category_learning_request_count,
                created_at,
                updated_at
            ) VALUES (:userId, :usageDate, 0, 0, 0, 0, 1, :now, :now)
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
