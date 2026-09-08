package com.be.userusage.service;

import com.be.userusage.dto.AdminUserUsageListResponse;
import com.be.userusage.dto.AdminUserUsageResponse;
import com.be.userusage.dto.UserUsagePeriod;
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

    private final UserUsageRepository userUsageRepository;

    public AdminUserUsageListResponse getAdminUserUsages(UserUsagePeriod period) {
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        LocalDate startDate = switch (period) {
            case TODAY -> today;
            case MONTH -> today.withDayOfMonth(1);
            case TOTAL -> LocalDate.of(1970, 1, 1);
        };
        List<AdminUserUsageResponse> users = userUsageRepository.findAdminUserUsages(startDate, today);
        return AdminUserUsageListResponse.from(period, users);
    }

    @Transactional
    public void recordCategoryKeywordJob(Long userId, long productCount) {
        validateCount(productCount);
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        Instant now = Instant.now();

        userUsageRepository.upsertCategoryKeywordUsage(
                userId,
                today,
                productCount,
                now
        );
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

}
