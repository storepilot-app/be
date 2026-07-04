package com.be.trainingproduct.client;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.config.properties.AiServerProperties;
import com.be.trainingproduct.dto.CategoryMatchMappingItem;
import com.be.trainingproduct.dto.ProductFeedbackAiRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class TrainingProductAiClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient.Builder restClientBuilder;
    private final AiServerProperties aiServerProperties;

    public ProductIndexRebuildResponse rebuildProductIndex(
            String userKey,
            List<MultipartFile> files,
            List<CategoryMatchMappingItem> mappings
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("userKey", userKey);
        try {
            body.add("categoryMappings", OBJECT_MAPPER.writeValueAsString(mappings));
        } catch (JsonProcessingException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "카테고리 매핑 정보를 변환하지 못했습니다."
            );
        }
        files.forEach(file -> body.add("files", file.getResource()));

        try {
            ProductIndexRebuildResponse response = restClient().post()
                    .uri("/ai/categories/product-index/rebuild")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ProductIndexRebuildResponse.class);
            if (response == null) {
                throw new BusinessException(
                        ErrorCode.CATEGORY_MATCHING_FAILED,
                        "AI 서버에서 상품 인덱스 재생성 결과를 받지 못했습니다."
                );
            }
            return response;
        } catch (RestClientException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스를 재생성하지 못했습니다."
            );
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
                throw new BusinessException(
                        ErrorCode.CATEGORY_MATCHING_FAILED,
                        "AI 서버에서 피드백 처리 결과를 받지 못했습니다."
                );
            }
            return response;
        } catch (RestClientException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스에 피드백을 반영하지 못했습니다."
            );
        }
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(aiServerProperties.baseUrl()).build();
    }
}
