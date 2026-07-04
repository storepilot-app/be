package com.be.categorymatcher.client;

import com.be.categorymatcher.dto.CategoryEmbeddingItem;
import com.be.categorymatcher.dto.CategoryEmbeddingRebuildRequest;
import com.be.categorymatcher.dto.CategoryMatchPredictRequest;
import com.be.categorymatcher.dto.CategoryMatchPredictResponse;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.global.config.properties.AiServerProperties;
import com.be.navercategory.domain.NaverCategory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class CategoryMatcherAiClient {
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
            // AI server can be started later; rule matching still works without cached embeddings.
        }
    }

    public Optional<CategoryMatchPredictResponse> predict(
            Long versionId,
            List<CategoryMatchProductRequest> products
    ) {
        try {
            CategoryMatchPredictResponse response = restClient().post()
                    .uri("/ai/categories/predict")
                    .body(new CategoryMatchPredictRequest(versionId, products))
                    .retrieve()
                    .body(CategoryMatchPredictResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException ignored) {
            return Optional.empty();
        }
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(aiServerProperties.baseUrl()).build();
    }
}
