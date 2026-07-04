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
        String trimmedUserKey = validateAndTrimUserKey(userKey);
        validateFiles(files);
        MyCategoryMappingVersion activeMappingVersion = getRequiredActiveMappingVersion(trimmedUserKey);
        List<CategoryMatchMappingItem> mappings = myCategoryMappingRepository
                .findByUserKeyAndVersionId(trimmedUserKey, activeMappingVersion.getId())
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
            throw invalid("활성화된 마이카테고리 매핑에 유효한 네이버 카테고리가 없습니다.");
        }
        return categoryMatcherAiClient.rebuildProductIndex(trimmedUserKey, files, mappings);
    }

    @Transactional
    public ProductCategoryFeedbackResponse addFeedback(ProductCategoryFeedbackRequest request) {
        if (request == null) {
            throw invalid("피드백 요청 정보가 필요합니다.");
        }
        String userKey = validateAndTrimUserKey(request.userKey());
        String productName = required(request.productName(), "상품명은 필수입니다.");
        String myCategoryCode = required(request.myCategoryCode(), "마이카테고리 코드는 필수입니다.");
        MyCategoryMappingVersion activeMappingVersion = getRequiredActiveMappingVersion(userKey);
        MyCategoryMapping mapping = myCategoryMappingRepository
                .findFirstByUserKeyAndVersionIdAndMyCategoryCode(
                        userKey,
                        activeMappingVersion.getId(),
                        myCategoryCode
                )
                .filter(item -> item.getNaverCategoryId() != null)
                .filter(item -> item.getNaverCategoryCode() != null && !item.getNaverCategoryCode().isBlank())
                .filter(item -> item.getNaverCategoryFullPath() != null && !item.getNaverCategoryFullPath().isBlank())
                .orElseThrow(() -> invalid("마이카테고리 코드에 대응하는 네이버 카테고리 매핑이 없습니다."));

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
                "상품 카테고리 수정 피드백이 저장되었습니다."
        );
    }

    private MyCategoryMappingVersion getRequiredActiveMappingVersion(String userKey) {
        return myCategoryMappingVersionRepository
                .findFirstByUserKeyAndActiveTrueOrderByUploadedAtDesc(userKey)
                .orElseThrow(() -> invalid("상품 인덱스를 생성하기 전에 마이카테고리 매핑을 업로드해 주세요."));
    }

    private void validateFiles(List<MultipartFile> files) {
        // 파일이 아예 없는 경우
        if (files == null || files.isEmpty()) {
            throw invalid("기존 상품 엑셀 파일을 하나 이상 업로드해 주세요.");
        }

        // 업로드된 파일 중 하나라도 잘못됐는지 확인
        boolean invalidFile = files.stream().anyMatch(file ->
                file == null
                        || file.isEmpty()
                        || file.getOriginalFilename() == null
                        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")
        );
        if (invalidFile) {
            throw invalid("기존 상품 파일은 비어 있지 않은 .xlsx 형식이어야 합니다.");
        }
    }

    private String validateAndTrimUserKey(String value) {
        return required(value, "사용자 식별자는 필수입니다.");
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
