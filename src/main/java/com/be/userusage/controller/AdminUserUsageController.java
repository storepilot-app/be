package com.be.userusage.controller;

import com.be.global.response.CommonResponse;
import com.be.userusage.dto.AdminUserUsageListResponse;
import com.be.userusage.dto.UserUsagePeriod;
import com.be.userusage.service.UserUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/user-usages")
@Tag(name = "관리자 사용자 사용량", description = "사용자별 서비스 사용량 조회 API")
@RequiredArgsConstructor
public class AdminUserUsageController {
    private final UserUsageService userUsageService;

    @Operation(summary = "사용자별 사용량 다건 조회")
    @GetMapping
    public CommonResponse<AdminUserUsageListResponse> getUserUsages(
            @RequestParam(defaultValue = "TODAY") UserUsagePeriod period
    ) {
        return CommonResponse.success(userUsageService.getAdminUserUsages(period));
    }
}
