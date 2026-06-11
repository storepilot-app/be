package com.be.navercategory.controller;

import com.be.global.response.CommonResponse;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.dto.NaverCategoryUploadResponse;
import com.be.navercategory.service.NaverCategoryUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/naver-categories")
@Tag(name = "Naver Categories", description = "Naver category upload and cache API")
@RequiredArgsConstructor
public class NaverCategoryController {
    private final NaverCategoryUploadService naverCategoryUploadService;

    @Operation(
            summary = "Upload Naver category excel",
            description = "Uploads Naver category excel, parses category rows, activates the new version, and regenerates CSV cache.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Upload successful",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CommonResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "versionId": 1,
                                                "sourceFilename": "naver_categories.xlsx",
                                                "rowCount": 5009,
                                                "categoryCount": 5009,
                                                "csvPath": "C:/Project/StorePilot/be/uploads/naver-categories/active/naver_categories.csv",
                                                "message": "Naver categories uploaded."
                                              },
                                              "message": "Naver categories uploaded.",
                                              "code": null,
                                              "errors": null
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<NaverCategoryUploadResponse> upload(
            @Parameter(description = "Naver category excel file(.xlsx, .xls)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        NaverCategoryVersion version = naverCategoryUploadService.upload(file);
        NaverCategoryUploadResponse response = new NaverCategoryUploadResponse(
                version.getId(),
                version.getSourceFilename(),
                version.getRowCount(),
                version.getCategoryCount(),
                version.getCsvFilePath(),
                "Naver categories uploaded."
        );
        return CommonResponse.success(response, response.message());
    }
}
