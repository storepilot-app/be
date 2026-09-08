package com.be.userusage.dto;

import java.util.List;

public record AdminUserUsageListResponse(
        UserUsagePeriod period,
        int userCount,
        List<AdminUserUsageResponse> users
) {
    public static AdminUserUsageListResponse from(
            UserUsagePeriod period,
            List<AdminUserUsageResponse> users
    ) {
        return new AdminUserUsageListResponse(period, users.size(), users);
    }
}
