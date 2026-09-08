package com.be.userusage.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.userusage.dto.AdminUserUsageListResponse;
import com.be.userusage.dto.AdminUserUsageResponse;
import com.be.userusage.dto.UserUsagePeriod;
import com.be.userusage.dto.UserUsageResponse;
import com.be.userusage.domain.UserUsage;
import com.be.userusage.repository.UserUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserUsageService {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final long DAILY_PRODUCT_LIMIT = 2_000;

    private final UserUsageRepository userUsageRepository;

    public UserUsageResponse getUserUsage(Long userId, UserUsagePeriod period) {
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        List<UserUsage> usages = userUsageRepository.findByUserIdAndUsageDateBetweenOrderByUsageDateDesc(
                userId,
                startDate(period, today),
                today
        );
        return new UserUsageResponse(
                period,
                usages.stream().mapToLong(UserUsage::getCategoryKeywordJobCount).sum(),
                usages.stream().mapToLong(UserUsage::getProcessedProductCount).sum(),
                usages.stream().mapToLong(UserUsage::getReservedProductCount).sum(),
                DAILY_PRODUCT_LIMIT,
                usages.stream().mapToLong(UserUsage::getImageDownloadCount).sum(),
                usages.stream().mapToLong(UserUsage::getCategoryLearningRequestCount).sum(),
                usages.isEmpty() ? null : usages.getFirst().getUsageDate()
        );
    }

    public AdminUserUsageListResponse getAdminUserUsages(UserUsagePeriod period) {
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        List<AdminUserUsageResponse> users = userUsageRepository.findAdminUserUsages(startDate(period, today), today);
        return AdminUserUsageListResponse.from(period, users);
    }

    @Transactional
    public LocalDate reserveCategoryProducts(Long userId, long productCount) {
        validateCount(productCount);
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        Instant now = Instant.now();
        userUsageRepository.ensureUsageExists(userId, today, now);
        int reserved = userUsageRepository.reserveProductsWithinDailyLimit(
                userId,
                today,
                productCount,
                DAILY_PRODUCT_LIMIT,
                now
        );
        if (reserved == 0) {
            throw new BusinessException(
                    ErrorCode.DAILY_PRODUCT_USAGE_LIMIT_EXCEEDED,
                    "카테고리 및 키워드 찾기는 하루 최대 2,000개 상품까지 이용할 수 있습니다."
            );
        }
        return today;
    }

    @Transactional
    public void completeCategoryKeywordJob(Long userId, LocalDate usageDate, long productCount) {
        validateCount(productCount);
        userUsageRepository.completeCategoryKeywordUsage(userId, usageDate, productCount, Instant.now());
    }

    @Transactional
    public void releaseCategoryProducts(Long userId, LocalDate usageDate, long productCount) {
        validateCount(productCount);
        userUsageRepository.releaseReservedProducts(userId, usageDate, productCount, Instant.now());
    }

    @Transactional
    public void recordImageDownloads(Long userId, long imageCount) {
        validateCount(imageCount);
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        Instant now = Instant.now();

        userUsageRepository.upsertImageDownloadUsage(
                userId,
                today,
                imageCount,
                now
        );
    }

    @Transactional
    public void recordCategoryLearningRequest(Long userId) {
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        Instant now = Instant.now();

        userUsageRepository.upsertCategoryLearningRequestUsage(
                userId,
                today,
                now
        );
    }

    private void validateCount(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("사용량은 음수일 수 없습니다.");
        }
    }

    private LocalDate startDate(UserUsagePeriod period, LocalDate today) {
        return switch (period) {
            case TODAY -> today;
            case MONTH -> today.withDayOfMonth(1);
            case TOTAL -> LocalDate.of(1970, 1, 1);
        };
    }

}
