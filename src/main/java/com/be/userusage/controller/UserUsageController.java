package com.be.userusage.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.userusage.dto.UserUsagePeriod;
import com.be.userusage.dto.UserUsageResponse;
import com.be.userusage.service.UserUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-usages")
@Tag(name = "사용자 사용량", description = "로그인 사용자의 서비스 사용량 조회 API")
@RequiredArgsConstructor
public class UserUsageController {
    private final UserUsageService userUsageService;

    @Operation(summary = "내 사용량 조회")
    @GetMapping("/me")
    public CommonResponse<UserUsageResponse> getMyUsage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "TODAY") UserUsagePeriod period
    ) {
        return CommonResponse.success(userUsageService.getUserUsage(loginUser.id(), period));
    }
}
