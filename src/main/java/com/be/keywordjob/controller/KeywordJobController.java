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
@Tag(name = "Keyword Jobs", description = "엑셀 기반 키워드 생성 작업 API")
@RequiredArgsConstructor
public class KeywordJobController {
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final KeywordJobUploadService keywordJobUploadService;
    private final KeywordExcelFillService keywordExcelFillService;

    @Operation(
            summary = "엑셀 업로드",
            description = "상품 엑셀 파일을 업로드하고 키워드 생성 작업을 PENDING 상태로 등록합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "작업 등록 성공",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CommonResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "jobId": 1,
                                                "status": "PENDING",
                                                "message": "키워드 생성 작업이 등록되었습니다."
                                              },
                                              "message": "키워드 생성 작업이 등록되었습니다.",
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
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "상품명 컬럼명", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "네이버 카테고리 컬럼명. 엑셀에 없으면 비워둘 수 있습니다.", example = "네이버 카테고리")
            @RequestParam(value = "categoryColumn", required = false, defaultValue = "") String categoryColumn,
            @Parameter(description = "상품당 생성 키워드 수. 기본값 30", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount
    ) {
        KeywordJob job = keywordJobUploadService.upload(file, productNameColumn, categoryColumn, keywordCount);
        KeywordJobUploadResponse response = new KeywordJobUploadResponse(
                job.getJobId(),
                job.getStatus(),
                "키워드 생성 작업이 등록되었습니다."
        );
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "엑셀 채우기 후 다운로드",
            description = "엑셀 파일을 업로드하면 L열에 키워드, T열에 마이카테를 채운 결과 파일을 바로 반환합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "결과 엑셀 다운로드",
                            content = @Content(mediaType = EXCEL_CONTENT_TYPE)
                    )
            }
    )
    @PostMapping(value = "/upload-download", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> uploadAndDownload(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "상품명 컬럼명", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "네이버 카테고리 컬럼명. 엑셀에 없으면 비워둘 수 있습니다.", example = "네이버 카테고리")
            @RequestParam(value = "categoryColumn", required = false, defaultValue = "") String categoryColumn,
            @Parameter(description = "상품당 생성 키워드 수. 기본값 30", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount
    ) {
        ExcelDownloadResult result = keywordExcelFillService.fillAndDownload(
                file,
                productNameColumn,
                categoryColumn,
                keywordCount
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
