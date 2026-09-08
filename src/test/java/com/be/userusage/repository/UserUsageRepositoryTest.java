package com.be.userusage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.be.auth.domain.StorePilotUser;
import com.be.auth.repository.StorePilotUserRepository;
import com.be.userusage.domain.UserUsage;
import java.time.LocalDate;
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
}
