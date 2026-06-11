package com.be.keywordjob.controller;

import com.be.keywordjob.domain.KeywordJob;
import com.be.keywordjob.dto.KeywordJobUploadResponse;
import com.be.global.ErrorResponse;
import com.be.keywordjob.service.KeywordJobUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/keyword-jobs")
@Tag(name = "Keyword Jobs", description = "엑셀 기반 키워드 생성 작업 API")
public class KeywordJobController {
    private final KeywordJobUploadService keywordJobUploadService;

    public KeywordJobController(KeywordJobUploadService keywordJobUploadService) {
        this.keywordJobUploadService = keywordJobUploadService;
    }

    @Operation(
            summary = "엑셀 업로드",
            description = "상품 엑셀 파일을 업로드하고 키워드 생성 작업을 PENDING 상태로 등록합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "작업 등록 성공",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = KeywordJobUploadResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "jobId": 1,
                                              "status": "PENDING",
                                              "message": "키워드 생성 작업이 등록되었습니다."
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "엑셀 파일 오류 또는 필수 요청 값 누락",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "errorCode": "INVALID_EXCEL_FILE",
                                              "message": "엑셀 파일 형식이 올바르지 않습니다."
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "요청 값 검증 실패",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KeywordJobUploadResponse upload(
            @Parameter(description = "상품 엑셀 파일(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "상품명 컬럼명", example = "상품명", required = true)
            @RequestParam("productNameColumn") String productNameColumn,
            @Parameter(description = "네이버 카테고리 컬럼명", example = "네이버 카테고리", required = true)
            @RequestParam("categoryColumn") String categoryColumn,
            @Parameter(description = "상품당 생성 키워드 수. 기본값 30", example = "30")
            @RequestParam(value = "keywordCount", required = false) Integer keywordCount
    ) {
        KeywordJob job = keywordJobUploadService.upload(file, productNameColumn, categoryColumn, keywordCount);
        return new KeywordJobUploadResponse(
                job.getJobId(),
                job.getStatus(),
                "키워드 생성 작업이 등록되었습니다."
        );
    }
}
