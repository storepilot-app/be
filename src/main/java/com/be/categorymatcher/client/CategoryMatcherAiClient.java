package com.be.categorymatcher.client;

import com.be.categorymatcher.dto.CategoryEmbeddingItem;
import com.be.categorymatcher.dto.CategoryEmbeddingRebuildRequest;
import com.be.categorymatcher.dto.CategoryMatchPredictRequest;
import com.be.categorymatcher.dto.CategoryMatchPredictResponse;
import com.be.categorymatcher.dto.CategoryMatchMappingItem;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.ProductFeedbackAiRequest;
import com.be.categorymatcher.dto.ProductFeedbackAiResponse;
import com.be.categorymatcher.dto.ProductIndexRebuildResponse;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.navercategory.domain.NaverCategory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class CategoryMatcherAiClient {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${storepilot.ai.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

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

    public ProductIndexRebuildResponse rebuildProductIndex(
            String userKey,
            List<MultipartFile> files,
            List<CategoryMatchMappingItem> mappings
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("userKey", userKey);
        try {
            body.part("categoryMappings", objectMapper.writeValueAsString(mappings));
        } catch (JsonProcessingException error) {
            throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "Failed to serialize category mappings.");
        }
        files.forEach(file -> body.part("files", file.getResource()));

        try {
            ProductIndexRebuildResponse response = restClient().post()
                    .uri("/ai/categories/product-index/rebuild")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(ProductIndexRebuildResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "AI server returned an empty product index response.");
            }
            return response;
        } catch (RestClientException error) {
            throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "Failed to rebuild the historical product index.");
        }
    }

    public ProductFeedbackAiResponse addProductFeedback(ProductFeedbackAiRequest request) {
        try {
            ProductFeedbackAiResponse response = restClient().post()
                    .uri("/ai/categories/product-index/feedback")
                    .body(request)
                    .retrieve()
                    .body(ProductFeedbackAiResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "AI server returned an empty feedback response.");
            }
            return response;
        } catch (RestClientException error) {
            throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "Failed to update the historical product index.");
        }
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(aiBaseUrl).build();
    }
}
