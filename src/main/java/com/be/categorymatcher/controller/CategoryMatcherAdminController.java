package com.be.categorymatcher.controller;

import com.be.categorymatcher.dto.CategoryEmbeddingRebuildResponse;
import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/category-matcher")
@Tag(name = "Category Matcher", description = "Category matching AI cache API")
@RequiredArgsConstructor
public class CategoryMatcherAdminController {
    private final CategoryMatcherService categoryMatcherService;

    @Operation(
            summary = "Rebuild active category embeddings",
            description = "Requests the AI server to rebuild embeddings for the active Naver category version.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Rebuild requested",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CommonResponse.class)
                            )
                    )
            }
    )
    @PostMapping("/embeddings/rebuild")
    public CommonResponse<CategoryEmbeddingRebuildResponse> rebuild() {
        Long versionId = categoryMatcherService.rebuildActiveEmbeddings().orElse(null);
        CategoryEmbeddingRebuildResponse response = new CategoryEmbeddingRebuildResponse(
                versionId,
                versionId == null ? "No active Naver category version." : "Category embeddings rebuild requested."
        );
        return CommonResponse.success(response, response.message());
    }
}
