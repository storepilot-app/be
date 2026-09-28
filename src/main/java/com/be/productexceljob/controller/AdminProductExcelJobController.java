package com.be.productexceljob.controller;

import com.be.global.response.CommonResponse;
import com.be.productexceljob.dto.AdminProductExcelJobResultResponse;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.service.ProductExcelJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/product-excel-jobs")
@Tag(name = "관리자 상품 엑셀 결과", description = "선택 과정 확인용 열이 포함된 상품 엑셀 결과 API")
@RequiredArgsConstructor
public class AdminProductExcelJobController {
    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductExcelJobService productExcelJobService;

    @Operation(summary = "관리자용 상품 엑셀 결과 목록 조회")
    @GetMapping("/results")
    public CommonResponse<List<AdminProductExcelJobResultResponse>> results() {
        return CommonResponse.success(productExcelJobService.getRecentAdminExcelResults());
    }

    @Operation(summary = "선택 과정이 포함된 관리자용 결과 다운로드")
    @GetMapping("/{jobId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long jobId) {
        ExcelDownloadResult result = productExcelJobService.getAdminExcelDownloadResult(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
