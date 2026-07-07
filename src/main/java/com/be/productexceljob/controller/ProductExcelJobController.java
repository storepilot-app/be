package com.be.productexceljob.controller;

import com.be.global.response.CommonResponse;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ProductExcelJobCreateResponse;
import com.be.productexceljob.dto.ProductExcelJobStatusResponse;
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
@Tag(name = "Product Excel Jobs", description = "Asynchronous product Excel processing API")
@RequiredArgsConstructor
public class ProductExcelJobController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductExcelJobService productExcelJobService;

    @Operation(summary = "Start category matching job")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductExcelJobCreateResponse> create(
            @Parameter(description = "Product Excel file", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "User key for my-category mapping", required = true)
            @RequestParam("userKey") String userKey
    ) {
        ProductExcelJobCreateResponse response = productExcelJobService.create(file, userKey);
        return CommonResponse.success(response, response.message());
    }

    @Operation(summary = "Get category matching progress")
    @GetMapping("/{jobId}/status")
    public CommonResponse<ProductExcelJobStatusResponse> status(@PathVariable long jobId) {
        return CommonResponse.success(productExcelJobService.status(jobId));
    }

    @Operation(summary = "Download completed category result")
    @GetMapping("/{jobId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long jobId) {
        ExcelDownloadResult result = productExcelJobService.download(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
