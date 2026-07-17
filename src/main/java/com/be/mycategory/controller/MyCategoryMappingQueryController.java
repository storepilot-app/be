package com.be.mycategory.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.dto.MyCategoryMappingListResponse;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my-category-mappings")
@Tag(name = "마이카테고리 매핑 조회", description = "현재 로그인 사용자의 마이카테고리 매핑 조회 API")
@RequiredArgsConstructor
public class MyCategoryMappingQueryController {
    private final MyCategoryMappingQueryService myCategoryMappingQueryService;

    @Operation(summary = "내 마이카테고리 매핑 목록 조회")
    @GetMapping
    public CommonResponse<MyCategoryMappingListResponse> getMyCategoryMappings(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        List<MyCategoryMapping> mappings = myCategoryMappingQueryService.getResolvedMappings(loginUser.id());
        return CommonResponse.success(MyCategoryMappingListResponse.from(mappings));
    }
}
