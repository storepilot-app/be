package com.be.productexceljob.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ProductImageDownloadPrepareResponse;
import com.be.productexceljob.dto.ProductImageDownloadRequest;
import com.be.productexceljob.dto.ProductExcelJobCreateResponse;
import com.be.productexceljob.dto.ProductExcelJobStatusResponse;
import com.be.productexceljob.service.ProductExcelProcessingService;
import com.be.productexceljob.service.ProductExcelJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/product-excel-jobs")
@Tag(name = "상품 엑셀 작업", description = "상품 엑셀 카테고리 분류 및 키워드 생성 비동기 작업 API")
@RequiredArgsConstructor
public class ProductExcelJobController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductExcelJobService productExcelJobService;
    private final ProductExcelProcessingService productExcelProcessingService;

    @Operation(summary = "상품 엑셀 처리 작업 시작")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductExcelJobCreateResponse> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "상품 엑셀 파일", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        ProductExcelJobCreateResponse response = productExcelJobService.create(file, loginUser.id());
        return CommonResponse.success(response, response.message());
    }

    @Operation(summary = "상품 엑셀 처리 진행률 조회")
    @GetMapping("/{jobId}/status")
    public CommonResponse<ProductExcelJobStatusResponse> status(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long jobId
    ) {
        return CommonResponse.success(productExcelJobService.status(jobId, loginUser.id()));
    }

    @Operation(summary = "완료된 상품 엑셀 결과 다운로드")
    @GetMapping("/{jobId}/download")
    public ResponseEntity<ByteArrayResource> download(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long jobId
    ) {
        ExcelDownloadResult result = productExcelJobService.download(jobId, loginUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }

    @Operation(
            summary = "상품 이미지 다운로드 목록 생성",
            description = "목록이미지1 컬럼의 이미지 URL을 읽어 브라우저 폴더 저장에 사용할 이미지 목록을 반환합니다."
    )
    @PostMapping(value = "/images/prepare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductImageDownloadPrepareResponse> prepareImageDownloads(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        ProductImageDownloadPrepareResponse response = productExcelProcessingService.prepareImageDownloads(file);
        return CommonResponse.success(response, "이미지 다운로드 목록을 생성했습니다.");
    }

    @Operation(summary = "상품 이미지 단건 다운로드")
    @PostMapping(value = "/images/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ByteArrayResource> downloadImage(
            @RequestBody ProductImageDownloadRequest request
    ) {
        byte[] image = productExcelProcessingService.downloadImage(request.url());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(image));
    }
}
