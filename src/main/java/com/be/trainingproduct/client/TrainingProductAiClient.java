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
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class TrainingProductAiClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient.Builder restClientBuilder;
    private final AiServerProperties aiServerProperties;

    public ProductIndexRebuildResponse rebuildProductIndex(
            Long userId,
            List<MultipartFile> files,
            List<CategoryMatchMappingItem> mappings
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("userId", userId);
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
            byte[] responseBody = restClient().post()
                    .uri("/ai/categories/product-index/rebuild")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .exchange((request, response) -> readResponseBytes(response));
            ProductIndexRebuildResponse response = readJsonResponse(responseBody, ProductIndexRebuildResponse.class);
            if (response == null) {
                throw new BusinessException(
                        ErrorCode.CATEGORY_MATCHING_FAILED,
                        "AI 서버에서 상품 인덱스 재생성 결과를 받지 못했습니다."
                );
            }
            return response;
        } catch (RestClientResponseException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스를 재생성하지 못했습니다: " + summarizeResponse(error)
            );
        } catch (AiServerResponseException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스를 재생성하지 못했습니다: " + summarizeAiServerResponse(error)
            );
        } catch (RestClientException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스를 재생성하지 못했습니다: " + summarizeMessage(error)
            );
        }
    }

    public ProductFeedbackAiResponse addProductFeedback(ProductFeedbackAiRequest request) {
        try {
            byte[] responseBody = restClient().post()
                    .uri("/ai/categories/product-index/feedback")
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) -> readResponseBytes(response));
            ProductFeedbackAiResponse response = readJsonResponse(responseBody, ProductFeedbackAiResponse.class);
            if (response == null) {
                throw new BusinessException(
                        ErrorCode.CATEGORY_MATCHING_FAILED,
                        "AI 서버에서 피드백 처리 결과를 받지 못했습니다."
                );
            }
            return response;
        } catch (RestClientResponseException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스에 피드백을 반영하지 못했습니다: " + summarizeResponse(error)
            );
        } catch (AiServerResponseException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스에 피드백을 반영하지 못했습니다: " + summarizeAiServerResponse(error)
            );
        } catch (RestClientException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "기존 상품 인덱스에 피드백을 반영하지 못했습니다: " + summarizeMessage(error)
            );
        }
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(aiServerProperties.baseUrl()).build();
    }

    private byte[] readResponseBytes(ClientHttpResponse response) throws IOException {
        byte[] body = response.getBody().readAllBytes();
        if (response.getStatusCode().isError()) {
            throw new AiServerResponseException(response.getStatusCode().toString(), body);
        }
        return body;
    }

    private <T> T readJsonResponse(byte[] body, Class<T> responseType) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(body, responseType);
        } catch (IOException error) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_MATCHING_FAILED,
                    "AI 서버 응답을 해석하지 못했습니다: " + summarizeMessage(error)
            );
        }
    }

    private String summarizeResponse(RestClientResponseException error) {
        String body = error.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return error.getStatusCode().toString();
        }
        return abbreviate(body);
    }

    private String summarizeAiServerResponse(AiServerResponseException error) {
        if (error.body().length == 0) {
            return error.status();
        }
        return error.status() + " " + abbreviate(new String(error.body()));
    }

    private String summarizeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return abbreviate(message);
    }

    private String abbreviate(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    private static class AiServerResponseException extends RuntimeException {
        private final String status;
        private final byte[] body;

        private AiServerResponseException(String status, byte[] body) {
            this.status = status;
            this.body = body == null ? new byte[0] : body;
        }

        private String status() {
            return status;
        }

        private byte[] body() {
            return body;
        }
    }
}
