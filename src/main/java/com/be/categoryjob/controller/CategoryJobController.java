package com.be.categoryjob.controller;

import com.be.categoryjob.dto.CategoryJobCreateResponse;
import com.be.categoryjob.dto.CategoryJobStatusResponse;
import com.be.categoryjob.service.CategoryJobService;
import com.be.global.response.CommonResponse;
import com.be.keywordjob.dto.ExcelDownloadResult;
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
@RequestMapping("/api/v1/category-jobs")
@Tag(name = "Category Jobs", description = "Asynchronous product category matching API")
@RequiredArgsConstructor
public class CategoryJobController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final CategoryJobService categoryJobService;

    @Operation(summary = "Start category matching job")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<CategoryJobCreateResponse> create(
            @Parameter(description = "Product Excel file", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "User key for my-category mapping", required = true)
            @RequestParam("userKey") String userKey
    ) {
        CategoryJobCreateResponse response = categoryJobService.create(file, userKey);
        return CommonResponse.success(response, response.message());
    }

    @Operation(summary = "Get category matching progress")
    @GetMapping("/{jobId}/status")
    public CommonResponse<CategoryJobStatusResponse> status(@PathVariable long jobId) {
        return CommonResponse.success(categoryJobService.status(jobId));
    }

    @Operation(summary = "Download completed category result")
    @GetMapping("/{jobId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long jobId) {
        ExcelDownloadResult result = categoryJobService.download(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
