package com.be.trainingproduct.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.domain.ProductCategoryFeedback;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.dto.ProductFeedbackAiRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiResponse;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCategoryFeedbackService {
    private final TrainingProductAiClient trainingProductAiClient;
    private final MyCategoryMappingQueryService myCategoryMappingQueryService;
    private final ProductCategoryFeedbackRepository productCategoryFeedbackRepository;
    private final ProductCategoryStatService productCategoryStatService;

    @Transactional
    public ProductCategoryFeedbackResponse addFeedback(Long userId, ProductCategoryFeedbackRequest request) {
        if (request == null) {
            throw invalid("피드백 요청 정보가 필요합니다.");
        }
        validateUserId(userId);
        String productName = required(request.productName(), "상품명은 필수입니다.");
        String myCategoryCode = required(request.myCategoryCode(), "마이카테고리 코드는 필수입니다.");
        MyCategoryMapping mapping = myCategoryMappingQueryService
                .getRequiredResolvedMapping(userId, myCategoryCode);
        String normalizedProductName = normalizeProductName(productName);
        String normalizedProductKey = normalizedProductKey(normalizedProductName);
        ProductCategoryFeedback previousFeedback = productCategoryFeedbackRepository
                .findFirstByUserIdAndNormalizedProductKeyOrderByCreatedAtDesc(userId, normalizedProductKey)
                .orElse(null);

        ProductCategoryFeedback feedback = productCategoryFeedbackRepository.save(ProductCategoryFeedback.create(
                userId,
                productName,
                normalizedProductName,
                normalizedProductKey,
                myCategoryCode,
                mapping.getNaverCategoryId(),
                mapping.getNaverCategoryCode(),
                mapping.getNaverCategoryFullPath(),
                Instant.now()
        ));
        ProductFeedbackAiResponse aiResponse = trainingProductAiClient.addProductFeedback(
                ProductFeedbackAiRequest.from(feedback)
        );
        if (previousFeedback == null) {
            productCategoryStatService.increaseStat(userId, mapping);
        } else {
            productCategoryStatService.moveStat(userId, previousFeedback.getNaverCategoryCode(), mapping);
        }
        return ProductCategoryFeedbackResponse.from(feedback, aiResponse);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw invalid("로그인이 필요합니다.");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private String normalizeProductName(String productName) {
        return productName == null
                ? ""
                : productName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizedProductKey(String normalizedProductName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedProductName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", error);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }
}
