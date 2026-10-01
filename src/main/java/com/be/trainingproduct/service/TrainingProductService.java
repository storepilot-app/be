package com.be.trainingproduct.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingUploadService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.dto.CategoryMatchMappingItem;
import com.be.trainingproduct.dto.ProductCategoryStatsResponse;
import com.be.trainingproduct.dto.ProductFeedbackBatchAiRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiRequest;
import com.be.trainingproduct.dto.ProductIndexAppendResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
import com.be.trainingproduct.dto.ProductMappingPreviewResponse;
import com.be.trainingproduct.service.TrainingProductExcelReader.TrainingProductExcelRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TrainingProductService {
    private final TrainingProductAiClient trainingProductAiClient;
    private final MyCategoryMappingUploadService myCategoryMappingUploadService;
    private final TrainingProductExcelReader trainingProductExcelReader;

    public ProductMappingPreviewResponse previewMappings(Long userId, MultipartFile file, MultipartFile myCategoryFile) {
        validateUserId(userId);
        trainingProductExcelReader.validateFiles(file == null ? List.of() : List.of(file));
        Map<String, MyCategoryMapping> mappings = new HashMap<>();
        myCategoryMappingUploadService.readMappings(myCategoryFile, userId)
                .forEach(mapping -> mappings.put(mapping.getMyCategoryCode(), mapping));
        List<ProductMappingPreviewResponse.Item> products = new ArrayList<>();
        for (TrainingProductExcelRow row : trainingProductExcelReader.readRows(file)) {
            MyCategoryMapping mapping = mappings.get(row.myCategoryCode());
            String reason = row.myCategoryCode().isBlank() ? "마이카테 코드가 없습니다."
                    : mapping == null ? "매핑 파일에 해당 마이카테 코드가 없습니다."
                    : mapping.getNaverCategoryId() == null ? "활성 네이버 카테고리에 없는 코드입니다." : null;
            products.add(new ProductMappingPreviewResponse.Item(
                    row.rowNumber(),
                    row.productName(),
                    row.myCategoryCode(),
                    mapping == null ? null : mapping.getNaverCategoryValue(),
                    mapping == null ? null : mapping.getNaverCategoryFullPath(),
                    reason
            ));
        }
        return new ProductMappingPreviewResponse(products);
    }

    public ProductIndexRebuildResponse rebuildIndex(
            Long userId,
            List<MultipartFile> files,
            MultipartFile myCategoryFile
    ) {
        validateUserId(userId);
        trainingProductExcelReader.validateFiles(files);
        List<MyCategoryMapping> resolvedMappings = myCategoryMappingUploadService.readResolvedMappings(
                myCategoryFile,
                userId
        );
        List<CategoryMatchMappingItem> mappings = resolvedMappings.stream()
                .map(CategoryMatchMappingItem::from)
                .toList();
        if (mappings.isEmpty()) {
            throw invalid("업로드한 마이카테고리 파일에 유효한 네이버 카테고리 매핑이 없습니다.");
        }
        ProductIndexRebuildResponse response = trainingProductAiClient.rebuildProductIndex(userId, files, mappings);
        return response;
    }

    public ProductCategoryStatsResponse getCategoryStats(Long userId) {
        validateUserId(userId);
        return trainingProductAiClient.getSharedCategoryStats();
    }

    public ProductIndexAppendResponse appendProducts(
            Long userId,
            List<MultipartFile> files,
            MultipartFile myCategoryFile
    ) {
        validateUserId(userId);
        trainingProductExcelReader.validateFiles(files);
        List<MyCategoryMapping> resolvedMappings = myCategoryMappingUploadService.readResolvedMappings(
                myCategoryFile,
                userId
        );
        if (resolvedMappings.isEmpty()) {
            throw invalid("업로드한 마이카테고리 파일에 유효한 네이버 카테고리 매핑이 없습니다.");
        }

        ProductAppendRows rows = collectProductAppendRows(
                trainingProductExcelReader.readRows(files),
                resolvedMappings
        );
        if (rows.candidates().isEmpty()) {
            throw invalid("기존 상품 인덱스에 추가할 수 있는 유효 상품 행이 없습니다.");
        }

        List<ProductFeedbackAiRequest> aiRequests = new ArrayList<>();
        for (ProductAppendCandidate candidate : rows.candidates()) {
            aiRequests.add(new ProductFeedbackAiRequest(
                    userId,
                    candidate.productName(),
                    candidate.mapping().getNaverCategoryId(),
                    candidate.mapping().getNaverCategoryCode(),
                    candidate.mapping().getNaverCategoryFullPath()
            ));
        }
        var aiResponse = trainingProductAiClient.appendProducts(
                new ProductFeedbackBatchAiRequest(userId, aiRequests)
        );

        return new ProductIndexAppendResponse(
                files.size(),
                rows.sourceRowCount(),
                rows.candidates().size(),
                rows.unmappedRowCount(),
                rows.candidates().size(),
                aiResponse.insertedProductCount(),
                aiResponse.updatedProductCount(),
                aiResponse.indexedProductCount(),
                "기존 상품 인덱스에 상품을 추가했습니다."
        );
    }

    private ProductAppendRows collectProductAppendRows(
            List<TrainingProductExcelRow> productRows,
            List<MyCategoryMapping> resolvedMappings
    ) {
        Map<String, MyCategoryMapping> mappingsByMyCategory = new HashMap<>();
        for (MyCategoryMapping mapping : resolvedMappings) {
            mappingsByMyCategory.put(mapping.getMyCategoryCode(), mapping);
        }

        List<ProductAppendCandidate> candidates = new ArrayList<>();
        int unmappedRowCount = 0;

        for (TrainingProductExcelRow productRow : productRows) {
            MyCategoryMapping mapping = mappingsByMyCategory.get(productRow.myCategoryCode());
            if (mapping == null) {
                unmappedRowCount++;
                continue;
            }
            candidates.add(new ProductAppendCandidate(productRow.productName(), mapping));
        }

        return new ProductAppendRows(productRows.size(), unmappedRowCount, candidates);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw invalid("로그인이 필요합니다.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }


    private record ProductAppendRows(
            int sourceRowCount,
            int unmappedRowCount,
            List<ProductAppendCandidate> candidates
    ) {
    }

    private record ProductAppendCandidate(
            String productName,
            MyCategoryMapping mapping
    ) {
    }

}
