package com.be.categorymatcher.controller;

import com.be.categorymatcher.dto.CategoryEmbeddingRebuildResponse;
import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
            description = "현재 활성화된 네이버 카테고리 버전을 기준으로 AI 서버의 카테고리 임베딩 캐시 재생성을 요청합니다."
    )
    @PostMapping("/embeddings/rebuild")
    public CommonResponse<CategoryEmbeddingRebuildResponse> rebuild() {
        Long versionId = categoryMatcherService.rebuildActiveEmbeddings().orElse(null);
        CategoryEmbeddingRebuildResponse response = versionId == null
                ? CategoryEmbeddingRebuildResponse.noActiveVersion()
                : CategoryEmbeddingRebuildResponse.requested(versionId);
        return CommonResponse.success(response, response.message());
    }
}
