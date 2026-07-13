package com.be.trainingproduct.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.trainingproduct.domain.ProductCategoryFeedback;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.dto.CategoryMatchMappingItem;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.dto.ProductFeedbackAiRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
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
    private final TrainingProductAiClient trainingProductAiClient;
    private final MyCategoryMappingQueryService myCategoryMappingQueryService;
    private final ProductCategoryFeedbackRepository productCategoryFeedbackRepository;

    public ProductIndexRebuildResponse rebuildIndex(Long userId, List<MultipartFile> files) {
        validateUserId(userId);
        validateFiles(files);
        List<CategoryMatchMappingItem> mappings = myCategoryMappingQueryService
                .getResolvedMappings(userId)
                .stream()
                .map(CategoryMatchMappingItem::from)
                .toList();
        if (mappings.isEmpty()) {
            throw invalid("활성화된 마이카테고리 매핑에 유효한 네이버 카테고리가 없습니다.");
        }
        return trainingProductAiClient.rebuildProductIndex(userId, files, mappings);
    }

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

        ProductCategoryFeedback feedback = productCategoryFeedbackRepository.save(ProductCategoryFeedback.create(
                userId,
                productName,
                myCategoryCode,
                mapping.getNaverCategoryId(),
                mapping.getNaverCategoryCode(),
                mapping.getNaverCategoryFullPath(),
                Instant.now()
        ));
        ProductFeedbackAiResponse aiResponse = trainingProductAiClient.addProductFeedback(
                ProductFeedbackAiRequest.from(feedback)
        );
        return ProductCategoryFeedbackResponse.from(feedback, aiResponse);
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
                        || !isExcelFilename(file.getOriginalFilename())
        );
        if (invalidFile) {
            throw invalid("기존 상품 파일은 비어 있지 않은 .xlsx 형식이어야 합니다.");
        }
    }

    private boolean isExcelFilename(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
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

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }
}
