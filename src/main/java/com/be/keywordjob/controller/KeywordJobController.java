package com.be.keywordjob.controller;

import com.be.keywordjob.dto.ImageZipDownloadResult;
import com.be.productexceljob.service.ProductExcelProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/keyword-jobs")
@Tag(name = "키워드 작업", description = "상품 엑셀 키워드 생성 및 이미지 다운로드 API")
@RequiredArgsConstructor
public class KeywordJobController {
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final ProductExcelProcessingService productExcelProcessingService;

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
