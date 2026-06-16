package com.be.keywordjob.controller;

import com.be.global.response.CommonResponse;
import com.be.keywordjob.domain.KeywordJob;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.keywordjob.dto.KeywordJobUploadResponse;
import com.be.keywordjob.service.KeywordExcelFillService;
import com.be.keywordjob.service.KeywordJobUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@Tag(name = "Keyword Jobs", description = "Excel keyword job API")
@RequiredArgsConstructor
public class KeywordJobController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final KeywordJobUploadService keywordJobUploadService;
    private final KeywordExcelFillService keywordExcelFillService;

    @Operation(
            summary = "Upload product excel",
            description = "Uploads a product excel file and registers a keyword job in PENDING state.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Job registered",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CommonResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "jobId": 1,
                                                "status": "PENDING",
                                                "message": "Keyword job registered."
                                              },
                                              "message": "Keyword job registered.",
                                              "code": null,
                                              "errors": null
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<KeywordJobUploadResponse> upload(
            @Parameter(description = "Product excel file(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Product name column", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "Naver category column. Leave empty when the excel has no category column.", example = "네이버 카테고리")
            @RequestParam(value = "categoryColumn", required = false, defaultValue = "") String categoryColumn,
            @Parameter(description = "Keyword count per product. Default is 30.", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount
    ) {
        KeywordJob job = keywordJobUploadService.upload(file, productNameColumn, categoryColumn, keywordCount);
        KeywordJobUploadResponse response = new KeywordJobUploadResponse(
                job.getJobId(),
                job.getStatus(),
                "Keyword job registered."
        );
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "Fill excel and download",
            description = "Fills keywords in column L, my category in column T, downloads images from 목록이미지1, and returns the result excel file.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Result excel download",
                            content = @Content(mediaType = EXCEL_CONTENT_TYPE)
                    )
            }
    )
    @PostMapping(value = "/upload-download", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> uploadAndDownload(
            @Parameter(description = "Product excel file(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Product name column", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "Naver category column. Leave empty when the excel has no category column.", example = "네이버 카테고리")
            @RequestParam(value = "categoryColumn", required = false, defaultValue = "") String categoryColumn,
            @Parameter(description = "Keyword count per product. Default is 30.", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount,
            @Parameter(description = "Image output directory. Leave empty to use uploads/product-images.", example = "C:\\StorePilot\\images")
            @RequestParam(value = "imageOutputDir", required = false, defaultValue = "") String imageOutputDir
    ) {
        ExcelDownloadResult result = keywordExcelFillService.fillAndDownload(
                file,
                productNameColumn,
                categoryColumn,
                keywordCount,
                imageOutputDir
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
