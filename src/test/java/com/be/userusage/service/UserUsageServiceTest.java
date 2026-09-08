package com.be.userusage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.userusage.domain.UserUsage;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
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

    @Test
    void reservesProductsWithinDailyLimit() {
        UserUsageRepository repository = mock(UserUsageRepository.class);
        when(repository.reserveProductsWithinDailyLimit(
                eq(1L),
                any(LocalDate.class),
                eq(1_500L),
                eq(2_000L),
                any()
        )).thenReturn(1);
        UserUsageService service = new UserUsageService(repository);

        LocalDate usageDate = service.reserveCategoryProducts(1L, 1_500);

        verify(repository).ensureUsageExists(eq(1L), eq(usageDate), any());
        verify(repository).reserveProductsWithinDailyLimit(
                eq(1L),
                eq(usageDate),
                eq(1_500L),
                eq(2_000L),
                any()
        );
    }

    @Test
    void rejectsProductsWhenDailyLimitWouldBeExceeded() {
        UserUsageRepository repository = mock(UserUsageRepository.class);
        when(repository.reserveProductsWithinDailyLimit(
                eq(1L),
                any(LocalDate.class),
                eq(501L),
                eq(2_000L),
                any()
        )).thenReturn(0);
        UserUsageService service = new UserUsageService(repository);

        assertThatThrownBy(() -> service.reserveCategoryProducts(1L, 501))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DAILY_PRODUCT_USAGE_LIMIT_EXCEEDED);
                    assertThat(error.getMessage()).contains("2,000개");
                });
    }
}
