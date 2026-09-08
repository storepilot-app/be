package com.be.userusage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.be.userusage.domain.UserUsage;
import com.be.userusage.dto.UserUsagePeriod;
import com.be.userusage.repository.UserUsageRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserUsageServiceTest {
    @Test
    void returnsSummedUsageForCurrentUser() {
        UserUsageRepository repository = mock(UserUsageRepository.class);
        UserUsage first = UserUsage.create(1L, LocalDate.of(2026, 9, 8));
        first.recordCategoryKeywordJob(20);
        UserUsage second = UserUsage.create(1L, LocalDate.of(2026, 9, 7));
        second.recordCategoryKeywordJob(30);
        second.recordImageDownloads(5);
        when(repository.findByUserIdAndUsageDateBetweenOrderByUsageDateDesc(
                eq(1L),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of(first, second));
        UserUsageService service = new UserUsageService(repository);

        var response = service.getUserUsage(1L, UserUsagePeriod.MONTH);

        assertThat(response.period()).isEqualTo(UserUsagePeriod.MONTH);
        assertThat(response.categoryKeywordJobCount()).isEqualTo(2);
        assertThat(response.processedProductCount()).isEqualTo(50);
        assertThat(response.imageDownloadCount()).isEqualTo(5);
        assertThat(response.categoryLearningRequestCount()).isZero();
        assertThat(response.lastUsedDate()).isEqualTo(LocalDate.of(2026, 9, 8));
    }
}
