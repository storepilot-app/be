package com.be.mycategory.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.dto.MyCategoryMappingListResponse;
import com.be.mycategory.dto.MyCategoryMappingUploadResponse;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.mycategory.service.MyCategoryMappingUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/my-category-mappings")
@Tag(name = "마이카테고리 매핑", description = "사용자별 마이카테고리와 네이버 카테고리의 매핑 관리 API")
@RequiredArgsConstructor
public class MyCategoryMappingController {
    private final MyCategoryMappingQueryService myCategoryMappingQueryService;
    private final MyCategoryMappingUploadService myCategoryMappingUploadService;

    @Operation(summary = "내 마이카테고리 매핑 목록 조회")
    @GetMapping
    public CommonResponse<MyCategoryMappingListResponse> getMyCategoryMappings(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        List<MyCategoryMapping> mappings = myCategoryMappingQueryService.getResolvedMappings(loginUser.id());
        return CommonResponse.success(MyCategoryMappingListResponse.from(mappings));
    }

    @Operation(
            summary = "마이카테고리 매핑 엑셀 업로드",
            description = "매핑 엑셀의 A열(마이카테고리)과 H열(네이버 카테고리 코드)을 읽어 사용자별 1:1 매핑으로 저장합니다."
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<MyCategoryMappingUploadResponse> upload(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "마이카테고리 매핑 엑셀 파일(.xlsx, .xls). A열은 마이카테고리, H열은 네이버 카테고리 코드입니다.", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        MyCategoryMappingVersion version = myCategoryMappingUploadService.upload(file, loginUser.id());
        MyCategoryMappingUploadResponse response = MyCategoryMappingUploadResponse.from(version);
        return CommonResponse.success(response, response.message());
    }
}
