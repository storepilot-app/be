package com.be.productimage.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.productimage.dto.ProductImageDownloadPrepareResponse;
import com.be.productimage.dto.ProductImageDownloadRequest;
import com.be.productimage.dto.ProductImageFailureExcelRequest;
import com.be.productimage.service.ProductImageDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/product-excel-jobs/images")
@Tag(name = "상품 이미지 다운로드", description = "상품 이미지 목록 준비, 다운로드 및 실패 목록 생성 API")
@RequiredArgsConstructor
public class ProductImageDownloadController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductImageDownloadService productImageDownloadService;

    @Operation(
            summary = "상품 이미지 다운로드 목록 생성",
            description = "목록이미지1 컬럼의 이미지 URL을 읽어 브라우저 폴더 저장에 사용할 이미지 목록을 반환합니다."
    )
    @PostMapping(value = "/prepare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductImageDownloadPrepareResponse> prepareImageDownloads(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        ProductImageDownloadPrepareResponse response = productImageDownloadService.prepareImageDownloads(file);
        return CommonResponse.success(response, "이미지 다운로드 목록을 생성했습니다.");
    }

    @Operation(summary = "상품 이미지 단건 다운로드")
    @PostMapping(value = "/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ByteArrayResource> downloadImage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody ProductImageDownloadRequest request
    ) {
        byte[] image = productImageDownloadService.downloadImage(
                request.url(),
                request.targetSizePercent(),
                loginUser.id(),
                Boolean.TRUE.equals(request.applyWatermark())
        );
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new ByteArrayResource(image));
    }

    @Operation(summary = "상품 이미지 다운로드 실패 목록 엑셀 생성")
    @PostMapping(value = "/failures/excel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ByteArrayResource> downloadImageFailures(
            @RequestBody ProductImageFailureExcelRequest request
    ) {
        byte[] content = productImageDownloadService.createImageFailureExcel(request.failures());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"image_download_failures.xlsx\"")
                .body(new ByteArrayResource(content));
    }
}
