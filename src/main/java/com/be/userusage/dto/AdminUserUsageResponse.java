package com.be.userusage.dto;

import com.be.auth.domain.UserRole;
import java.time.LocalDate;

public record AdminUserUsageResponse(
        Long userId,
        String email,
        UserRole role,
        long categoryKeywordJobCount,
        long processedProductCount,
        long imageDownloadCount,
        long categoryLearningRequestCount,
        LocalDate lastUsedDate
) {
}
