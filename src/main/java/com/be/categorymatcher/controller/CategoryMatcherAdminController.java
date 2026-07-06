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
@Tag(name = "카테고리 매처", description = "카테고리 분류용 AI 캐시 관리 API")
@RequiredArgsConstructor
public class CategoryMatcherAdminController {
    private final CategoryMatcherService categoryMatcherService;

    @Operation(
            summary = "활성 네이버 카테고리 임베딩 재생성",
            description = "현재 활성화된 네이버 카테고리 버전을 기준으로 AI 서버의 카테고리 임베딩 캐시 재생성을 요청합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "재생성 요청 완료",
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
                versionId == null ? "활성화된 네이버 카테고리 버전이 없습니다." : "카테고리 임베딩 재생성을 요청했습니다."
        );
        return CommonResponse.success(response, response.message());
    }
}
