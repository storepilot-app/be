package com.be.trainingproduct.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchMappingItem;
import com.be.categorymatcher.dto.ProductFeedbackAiRequest;
import com.be.categorymatcher.dto.ProductFeedbackAiResponse;
import com.be.categorymatcher.dto.ProductIndexRebuildResponse;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.mycategory.repository.MyCategoryMappingVersionRepository;
import com.be.trainingproduct.domain.ProductCategoryFeedback;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TrainingProductService {
    private final CategoryMatcherAiClient categoryMatcherAiClient;
    private final MyCategoryMappingRepository myCategoryMappingRepository;
    private final MyCategoryMappingVersionRepository myCategoryMappingVersionRepository;
    private final ProductCategoryFeedbackRepository productCategoryFeedbackRepository;

    public ProductIndexRebuildResponse rebuildIndex(String userKey, List<MultipartFile> files) {
        String normalizedUserKey = normalizeUserKey(userKey);
        validateFiles(files);
        MyCategoryMappingVersion version = activeMappingVersion(normalizedUserKey);
        List<CategoryMatchMappingItem> mappings = myCategoryMappingRepository
                .findByUserKeyAndVersionId(normalizedUserKey, version.getId())
                .stream()
                .filter(mapping -> mapping.getNaverCategoryId() != null)
                .filter(mapping -> mapping.getNaverCategoryCode() != null && !mapping.getNaverCategoryCode().isBlank())
                .filter(mapping -> mapping.getNaverCategoryFullPath() != null && !mapping.getNaverCategoryFullPath().isBlank())
                .map(mapping -> new CategoryMatchMappingItem(
                        mapping.getMyCategoryCode(),
                        mapping.getNaverCategoryId(),
                        mapping.getNaverCategoryCode(),
                        mapping.getNaverCategoryFullPath()
                ))
                .toList();
        if (mappings.isEmpty()) {
            throw invalid("Active my-category mappings contain no resolved Naver categories.");
        }
        return categoryMatcherAiClient.rebuildProductIndex(normalizedUserKey, files, mappings);
    }

    @Transactional
    public ProductCategoryFeedbackResponse addFeedback(ProductCategoryFeedbackRequest request) {
        if (request == null) {
            throw invalid("Feedback request is required.");
        }
        String userKey = normalizeUserKey(request.userKey());
        String productName = required(request.productName(), "Product name is required.");
        String myCategoryCode = required(request.myCategoryCode(), "My category code is required.");
        MyCategoryMappingVersion version = activeMappingVersion(userKey);
        MyCategoryMapping mapping = myCategoryMappingRepository
                .findFirstByUserKeyAndVersionIdAndMyCategoryCode(userKey, version.getId(), myCategoryCode)
                .filter(item -> item.getNaverCategoryId() != null)
                .filter(item -> item.getNaverCategoryCode() != null && !item.getNaverCategoryCode().isBlank())
                .filter(item -> item.getNaverCategoryFullPath() != null && !item.getNaverCategoryFullPath().isBlank())
                .orElseThrow(() -> invalid("My category code has no Naver category mapping."));

        ProductCategoryFeedback feedback = productCategoryFeedbackRepository.save(new ProductCategoryFeedback(
                userKey,
                productName,
                myCategoryCode,
                mapping.getNaverCategoryId(),
                mapping.getNaverCategoryCode(),
                mapping.getNaverCategoryFullPath(),
                Instant.now()
        ));
        ProductFeedbackAiResponse aiResponse = categoryMatcherAiClient.addProductFeedback(
                new ProductFeedbackAiRequest(
                        userKey,
                        productName,
                        mapping.getNaverCategoryId(),
                        mapping.getNaverCategoryCode(),
                        mapping.getNaverCategoryFullPath()
                )
        );
        return new ProductCategoryFeedbackResponse(
                feedback.getId(),
                userKey,
                myCategoryCode,
                mapping.getNaverCategoryFullPath(),
                aiResponse.indexedProductCount(),
                "Product category feedback saved."
        );
    }

    private MyCategoryMappingVersion activeMappingVersion(String userKey) {
        return myCategoryMappingVersionRepository
                .findFirstByUserKeyAndActiveTrueOrderByUploadedAtDesc(userKey)
                .orElseThrow(() -> invalid("Upload my category mappings before building the product index."));
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalid("At least one historical product Excel file is required.");
        }
        boolean invalidFile = files.stream().anyMatch(file ->
                file == null
                        || file.isEmpty()
                        || file.getOriginalFilename() == null
                        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")
        );
        if (invalidFile) {
            throw invalid("Historical product files must be non-empty .xlsx files.");
        }
    }

    private String normalizeUserKey(String value) {
        return required(value, "User key is required.");
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }
}
