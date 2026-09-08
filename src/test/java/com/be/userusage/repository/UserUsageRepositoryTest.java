package com.be.userusage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.be.auth.domain.StorePilotUser;
import com.be.auth.repository.StorePilotUserRepository;
import com.be.userusage.domain.UserUsage;
import java.time.LocalDate;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserUsageRepositoryTest {
    @Autowired
    private UserUsageRepository userUsageRepository;

    @Autowired
    private StorePilotUserRepository userRepository;

    @Test
    void aggregatesUsageWithinRequestedDateRange() {
        StorePilotUser user = userRepository.save(StorePilotUser.create(
                "usage-test@example.com",
                "password-hash",
                true
        ));
        LocalDate today = LocalDate.of(2026, 9, 8);
        UserUsage previousMonthUsage = UserUsage.create(user.getId(), LocalDate.of(2026, 8, 31));
        previousMonthUsage.recordCategoryKeywordJob(30);
        UserUsage todayUsage = UserUsage.create(user.getId(), today);
        todayUsage.recordCategoryKeywordJob(20);
        todayUsage.recordImageDownloads(5);
        todayUsage.recordCategoryLearningRequest();
        userUsageRepository.save(previousMonthUsage);
        userUsageRepository.save(todayUsage);
        userUsageRepository.flush();

        var usage = userUsageRepository
                .findAdminUserUsages(today.withDayOfMonth(1), today)
                .stream()
                .filter(item -> item.userId().equals(user.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(usage.categoryKeywordJobCount()).isEqualTo(1);
        assertThat(usage.processedProductCount()).isEqualTo(20);
        assertThat(usage.imageDownloadCount()).isEqualTo(5);
        assertThat(usage.categoryLearningRequestCount()).isEqualTo(1);
        assertThat(usage.lastUsedDate()).isEqualTo(today);
    }

    @Test
    void reservesAndCompletesProductsWithoutExceedingDailyLimit() {
        LocalDate usageDate = LocalDate.of(2026, 9, 8);
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        Long userId = 100L;
        userUsageRepository.ensureUsageExists(userId, usageDate, now);

        int firstReservation = userUsageRepository.reserveProductsWithinDailyLimit(
                userId,
                usageDate,
                1_500,
                2_000,
                now
        );
        int secondReservation = userUsageRepository.reserveProductsWithinDailyLimit(
                userId,
                usageDate,
                500,
                2_000,
                now
        );
        int rejectedReservation = userUsageRepository.reserveProductsWithinDailyLimit(
                userId,
                usageDate,
                1,
                2_000,
                now
        );
        userUsageRepository.completeCategoryKeywordUsage(userId, usageDate, 1_500, now);
        userUsageRepository.completeCategoryKeywordUsage(userId, usageDate, 500, now);
        userUsageRepository.flush();

        UserUsage usage = userUsageRepository.findByUserIdAndUsageDateBetweenOrderByUsageDateDesc(
                userId,
                usageDate,
                usageDate
        ).getFirst();
        assertThat(firstReservation).isEqualTo(1);
        assertThat(secondReservation).isEqualTo(1);
        assertThat(rejectedReservation).isZero();
        assertThat(usage.getCategoryKeywordJobCount()).isEqualTo(2);
        assertThat(usage.getProcessedProductCount()).isEqualTo(2_000);
        assertThat(usage.getReservedProductCount()).isZero();
    }
}
