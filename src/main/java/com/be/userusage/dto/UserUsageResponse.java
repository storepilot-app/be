package com.be.userusage.dto;

import java.time.LocalDate;

public record UserUsageResponse(
        UserUsagePeriod period,
        long categoryKeywordJobCount,
        long processedProductCount,
        long imageDownloadCount,
        long categoryLearningRequestCount,
        LocalDate lastUsedDate
) {
}
