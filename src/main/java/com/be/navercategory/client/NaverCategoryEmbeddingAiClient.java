package com.be.navercategory.client;

import com.be.global.config.properties.AiServerProperties;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.dto.CategoryEmbeddingItem;
import com.be.navercategory.dto.CategoryEmbeddingRebuildRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class NaverCategoryEmbeddingAiClient {
    private final RestClient.Builder restClientBuilder;
    private final AiServerProperties aiServerProperties;

    public void rebuild(Long versionId, List<NaverCategory> categories) {
        List<CategoryEmbeddingItem> items = categories.stream()
                .map(category -> new CategoryEmbeddingItem(
                        category.getId(),
                        category.getCategoryCode(),
                        category.getFullPath(),
                        category.getSearchText()
                ))
                .toList();
        CategoryEmbeddingRebuildRequest request = new CategoryEmbeddingRebuildRequest(versionId, items);

        try {
            restClient().post()
                    .uri("/ai/categories/rebuild")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // AI 서버가 나중에 실행될 수 있으므로 임베딩 캐시 재생성 실패는 업로드 흐름을 막지 않는다.
        }
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(aiServerProperties.baseUrl()).build();
    }
}
