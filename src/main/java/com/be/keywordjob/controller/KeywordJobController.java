package com.be.keywordjob.controller;

import com.be.global.response.CommonResponse;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.keywordjob.dto.ImageDownloadResponse;
import com.be.keywordjob.dto.ImageZipDownloadResult;
import com.be.productexceljob.service.ProductExcelProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final ProductExcelProcessingService productExcelProcessingService;

    @Operation(
            summary = "상품 엑셀 작성 후 다운로드",
            description = "L열에 키워드, T열에 마이카테고리, U열에 네이버 카테고리, AA열에 상품명, AB~AK열에 Top-10 네이버 카테고리 후보와 점수, AL열에 LLM 선택 카테고리, AM열에 LLM 상태를 작성한 뒤 결과 엑셀 파일을 반환합니다."
    )
    @PostMapping(value = "/upload-download", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> uploadAndDownload(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "상품명 컬럼명", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "네이버 카테고리 컬럼명. 엑셀에 카테고리 컬럼이 없으면 비워둡니다.", example = "네이버 카테고리")
            @RequestParam(value = "categoryColumn", required = false, defaultValue = "") String categoryColumn,
            @Parameter(description = "상품당 생성할 키워드 수. 기본값은 30입니다.", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount,
            @Parameter(description = "마이카테고리 매핑을 조회할 사용자 식별자", example = "user-a")
            @RequestParam(value = "userKey", required = false, defaultValue = "") String userKey
    ) {
        ExcelDownloadResult result = productExcelProcessingService.fillAndDownload(
                file,
                productNameColumn,
                categoryColumn,
                keywordCount,
                userKey
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }

    @Operation(
            summary = "상품 이미지 다운로드",
            description = "목록이미지1 컬럼의 이미지 URL을 읽어 지정한 서버 디렉터리에 이미지를 저장합니다."
    )
    @PostMapping(value = "/images/download", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ImageDownloadResponse> downloadImages(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "이미지 저장 디렉터리. 비워두면 uploads/product-images를 사용합니다.", example = "C:\\StorePilot\\images")
            @RequestParam(value = "imageOutputDir", required = false, defaultValue = "") String imageOutputDir
    ) {
        ImageDownloadResponse response = productExcelProcessingService.downloadImages(file, imageOutputDir);
        return CommonResponse.success(response, response.message());
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
