package com.be.userusage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserUsageTest {
    @Test
    void recordsDailyUsage() {
        LocalDate usageDate = LocalDate.of(2026, 9, 8);
        UserUsage usage = UserUsage.create(1L, usageDate);

        usage.recordCategoryKeywordJob(30);
        usage.recordCategoryKeywordJob(20);
        usage.recordImageDownloads(10);
        usage.recordCategoryLearningRequest();

        assertEquals(1L, usage.getUserId());
        assertEquals(usageDate, usage.getUsageDate());
        assertEquals(2, usage.getCategoryKeywordJobCount());
        assertEquals(50, usage.getProcessedProductCount());
        assertEquals(10, usage.getImageDownloadCount());
        assertEquals(1, usage.getCategoryLearningRequestCount());
    }

    @Test
    void rejectsNegativeUsage() {
        UserUsage usage = UserUsage.create(1L, LocalDate.of(2026, 9, 8));

        assertThrows(IllegalArgumentException.class, () -> usage.recordCategoryKeywordJob(-1));
        assertThrows(IllegalArgumentException.class, () -> usage.recordImageDownloads(-1));
    }
}
