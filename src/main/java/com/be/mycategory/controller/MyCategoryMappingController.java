package com.be.mycategory.controller;

import com.be.global.response.CommonResponse;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.dto.MyCategoryMappingUploadResponse;
import com.be.mycategory.service.MyCategoryMappingUploadService;
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
@RequestMapping("/api/v1/admin/my-category-mappings")
@Tag(name = "My Category Mappings", description = "User my category to Naver category mapping API")
@RequiredArgsConstructor
public class MyCategoryMappingController {
    private final MyCategoryMappingUploadService myCategoryMappingUploadService;

    @Operation(
            summary = "Upload my category mapping excel",
            description = "Uploads a mapping excel and saves one-to-one mappings from column A(my category) to column H(Naver category) for each user.",
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
                                                "userKey": "user-a",
                                                "sourceFilename": "my_categories.xlsx",
                                                "rowCount": 120,
                                                "mappingCount": 120,
                                                "matchedCount": 118,
                                                "uploadedFilePath": "C:/Project/StorePilot/be/uploads/my-category-mappings/user-a/versions/...",
                                                "message": "My category mappings uploaded."
                                              },
                                              "message": "My category mappings uploaded.",
                                              "code": null,
                                              "errors": null
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<MyCategoryMappingUploadResponse> upload(
            @Parameter(description = "User key. This preserves each user's own my category number system.", example = "user-a", required = true)
            @RequestParam("userKey") String userKey,
            @Parameter(description = "My category mapping excel file(.xlsx, .xls). Column A is my category, column H is Naver category.", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        MyCategoryMappingVersion version = myCategoryMappingUploadService.upload(file, userKey);
        MyCategoryMappingUploadResponse response = new MyCategoryMappingUploadResponse(
                version.getId(),
                version.getUserKey(),
                version.getSourceFilename(),
                version.getRowCount(),
                version.getMappingCount(),
                version.getMatchedCount(),
                version.getUploadedFilePath(),
                "My category mappings uploaded."
        );
        return CommonResponse.success(response, response.message());
    }
}
