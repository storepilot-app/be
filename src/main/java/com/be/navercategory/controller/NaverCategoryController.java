package com.be.navercategory.controller;

import com.be.global.response.CommonResponse;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.dto.NaverCategoryUploadResponse;
import com.be.navercategory.service.NaverCategoryUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/naver-categories")
@Tag(name = "네이버 카테고리", description = "네이버 카테고리 업로드 및 캐시 관리 API")
@RequiredArgsConstructor
public class NaverCategoryController {
    private final NaverCategoryUploadService naverCategoryUploadService;

    @Operation(
            summary = "네이버 카테고리 엑셀 업로드",
            description = "네이버 카테고리 엑셀을 해석하여 새 버전을 활성화하고 CSV 캐시와 카테고리 임베딩을 재생성합니다."
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<NaverCategoryUploadResponse> upload(
            @Parameter(description = "네이버 카테고리 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        NaverCategoryVersion version = naverCategoryUploadService.upload(file);
        NaverCategoryUploadResponse response = NaverCategoryUploadResponse.from(version);
        return CommonResponse.success(response, response.message());
    }
}
