package com.be.productexceljob.controller;

import com.be.global.response.CommonResponse;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ImageZipDownloadResult;
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
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final ProductExcelJobService productExcelJobService;
    private final ProductExcelProcessingService productExcelProcessingService;

    @Operation(summary = "상품 엑셀 처리 작업 시작")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductExcelJobCreateResponse> create(
            @Parameter(description = "상품 엑셀 파일", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "마이카테고리 매핑을 조회할 사용자 식별자", required = true)
            @RequestParam("userKey") String userKey
    ) {
        ProductExcelJobCreateResponse response = productExcelJobService.create(file, userKey);
        return CommonResponse.success(response, response.message());
    }

    @Operation(summary = "상품 엑셀 처리 진행률 조회")
    @GetMapping("/{jobId}/status")
    public CommonResponse<ProductExcelJobStatusResponse> status(@PathVariable long jobId) {
        return CommonResponse.success(productExcelJobService.status(jobId));
    }

    @Operation(summary = "완료된 상품 엑셀 결과 다운로드")
    @GetMapping("/{jobId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long jobId) {
        ExcelDownloadResult result = productExcelJobService.download(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }

    @Operation(
            summary = "상품 이미지 ZIP 다운로드",
            description = "목록이미지1 컬럼의 이미지 URL을 읽어 이미지를 다운로드하고 ZIP 파일로 반환합니다."
    )
    @PostMapping(value = "/images/download-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> downloadImagesZip(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        ImageZipDownloadResult result = productExcelProcessingService.downloadImagesAsZip(file);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ZIP_CONTENT_TYPE))
                .header("X-Saved-Image-Count", String.valueOf(result.savedCount()))
                .header("X-Failed-Image-Count", String.valueOf(result.failedCount()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
