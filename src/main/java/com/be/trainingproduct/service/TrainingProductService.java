package com.be.trainingproduct.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.trainingproduct.domain.ProductCategoryFeedback;
import com.be.trainingproduct.domain.ProductCategoryStat;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.dto.CategoryMatchMappingItem;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.dto.ProductCategoryStatsResponse;
import com.be.trainingproduct.dto.ProductFeedbackAiRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiResponse;
import com.be.trainingproduct.dto.ProductIndexAppendResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TrainingProductService {
    private static final int PRODUCT_NAME_COLUMN_INDEX = 3; // D
    private static final int MY_CATEGORY_COLUMN_INDEX = 19; // T

    private final TrainingProductAiClient trainingProductAiClient;
    private final MyCategoryMappingQueryService myCategoryMappingQueryService;
    private final ProductCategoryFeedbackRepository productCategoryFeedbackRepository;
    private final ProductCategoryStatService productCategoryStatService;

    public ProductIndexRebuildResponse rebuildIndex(Long userId, List<MultipartFile> files) {
        validateUserId(userId);
        validateFiles(files);
        List<MyCategoryMapping> resolvedMappings = myCategoryMappingQueryService.getResolvedMappings(userId);
        List<CategoryMatchMappingItem> mappings = resolvedMappings.stream()
                .map(CategoryMatchMappingItem::from)
                .toList();
        if (mappings.isEmpty()) {
            throw invalid("활성화된 마이카테고리 매핑에 유효한 네이버 카테고리가 없습니다.");
        }
        List<ProductCategoryStat> stats = collectCategoryStats(userId, files, resolvedMappings);
        ProductIndexRebuildResponse response = trainingProductAiClient.rebuildProductIndex(userId, files, mappings);
        productCategoryStatService.replaceStats(userId, stats);
        return response;
    }

    public ProductCategoryStatsResponse getCategoryStats(Long userId) {
        validateUserId(userId);
        return productCategoryStatService.getStats(userId);
    }

    public ProductIndexAppendResponse appendProducts(Long userId, List<MultipartFile> files) {
        validateUserId(userId);
        validateFiles(files);
        List<MyCategoryMapping> resolvedMappings = myCategoryMappingQueryService.getResolvedMappings(userId);
        if (resolvedMappings.isEmpty()) {
            throw invalid("활성화된 마이카테고리 매핑에 유효한 네이버 카테고리가 없습니다.");
        }

        ProductAppendRows rows = collectProductAppendRows(files, resolvedMappings);
        if (rows.candidates().isEmpty()) {
            throw invalid("기존 상품 인덱스에 추가할 수 있는 유효 상품 행이 없습니다.");
        }

        int indexedProductCount = 0;
        for (ProductAppendCandidate candidate : rows.candidates()) {
            ProductCategoryFeedback feedback = productCategoryFeedbackRepository.save(ProductCategoryFeedback.create(
                    userId,
                    candidate.productName(),
                    candidate.mapping().getMyCategoryCode(),
                    candidate.mapping().getNaverCategoryId(),
                    candidate.mapping().getNaverCategoryCode(),
                    candidate.mapping().getNaverCategoryFullPath(),
                    Instant.now()
            ));
            ProductFeedbackAiResponse aiResponse = trainingProductAiClient.addProductFeedback(
                    ProductFeedbackAiRequest.from(feedback)
            );
            productCategoryStatService.increaseStat(userId, candidate.mapping());
            indexedProductCount = aiResponse.indexedProductCount();
        }

        return new ProductIndexAppendResponse(
                files.size(),
                rows.sourceRowCount(),
                rows.candidates().size(),
                rows.unmappedRowCount(),
                rows.candidates().size(),
                indexedProductCount,
                "기존 상품 인덱스에 상품을 추가했습니다."
        );
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
        productCategoryStatService.increaseStat(userId, mapping);
        return ProductCategoryFeedbackResponse.from(feedback, aiResponse);
    }

    private ProductAppendRows collectProductAppendRows(
            List<MultipartFile> files,
            List<MyCategoryMapping> resolvedMappings
    ) {
        Map<String, MyCategoryMapping> mappingsByMyCategory = new HashMap<>();
        for (MyCategoryMapping mapping : resolvedMappings) {
            mappingsByMyCategory.put(mapping.getMyCategoryCode(), mapping);
        }

        List<ProductAppendCandidate> candidates = new java.util.ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        int sourceRowCount = 0;
        int unmappedRowCount = 0;

        for (MultipartFile file : files) {
            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String productName = formatter.formatCellValue(row.getCell(PRODUCT_NAME_COLUMN_INDEX)).trim();
                    if (productName.isBlank()) {
                        continue;
                    }
                    sourceRowCount++;

                    String myCategoryCode = formatter.formatCellValue(row.getCell(MY_CATEGORY_COLUMN_INDEX)).trim();
                    MyCategoryMapping mapping = mappingsByMyCategory.get(myCategoryCode);
                    if (mapping == null) {
                        unmappedRowCount++;
                        continue;
                    }

                    candidates.add(new ProductAppendCandidate(productName, mapping));
                }
            } catch (IOException e) {
                throw invalid("기존 상품 엑셀 파일을 읽지 못했습니다.");
            }
        }

        return new ProductAppendRows(sourceRowCount, unmappedRowCount, candidates);
    }

    private List<ProductCategoryStat> collectCategoryStats(
            Long userId,
            List<MultipartFile> files,
            List<MyCategoryMapping> resolvedMappings
    ) {
        Map<String, MyCategoryMapping> mappingsByMyCategory = new HashMap<>();
        for (MyCategoryMapping mapping : resolvedMappings) {
            mappingsByMyCategory.put(mapping.getMyCategoryCode(), mapping);
        }

        Map<String, CategoryCount> countsByCategoryCode = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        for (MultipartFile file : files) {
            collectCategoryStats(file, mappingsByMyCategory, countsByCategoryCode, formatter);
        }

        Instant updatedAt = Instant.now();
        return countsByCategoryCode.values()
                .stream()
                .sorted(Comparator
                        .comparingLong(CategoryCount::productCount)
                        .reversed()
                        .thenComparing(CategoryCount::naverCategoryFullPath))
                .map(count -> ProductCategoryStat.create(
                        userId,
                        count.naverCategoryId(),
                        count.naverCategoryCode(),
                        count.naverCategoryFullPath(),
                        count.productCount(),
                        updatedAt
                ))
                .toList();
    }

    private void collectCategoryStats(
            MultipartFile file,
            Map<String, MyCategoryMapping> mappingsByMyCategory,
            Map<String, CategoryCount> countsByCategoryCode,
            DataFormatter formatter
    ) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String productName = formatter.formatCellValue(row.getCell(PRODUCT_NAME_COLUMN_INDEX)).trim();
                if (productName.isBlank()) {
                    continue;
                }

                String myCategoryCode = formatter.formatCellValue(row.getCell(MY_CATEGORY_COLUMN_INDEX)).trim();
                MyCategoryMapping mapping = mappingsByMyCategory.get(myCategoryCode);
                if (mapping == null) {
                    continue;
                }

                countsByCategoryCode
                        .computeIfAbsent(mapping.getNaverCategoryCode(), ignored -> CategoryCount.from(mapping))
                        .increment();
            }
        } catch (IOException e) {
            throw invalid("기존 상품 엑셀 파일을 읽지 못했습니다.");
        }
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

    private static class CategoryCount {
        private final Long naverCategoryId;
        private final String naverCategoryCode;
        private final String naverCategoryFullPath;
        private long productCount;

        private CategoryCount(
                Long naverCategoryId,
                String naverCategoryCode,
                String naverCategoryFullPath
        ) {
            this.naverCategoryId = naverCategoryId;
            this.naverCategoryCode = naverCategoryCode;
            this.naverCategoryFullPath = naverCategoryFullPath;
        }

        private static CategoryCount from(MyCategoryMapping mapping) {
            return new CategoryCount(
                    mapping.getNaverCategoryId(),
                    mapping.getNaverCategoryCode(),
                    mapping.getNaverCategoryFullPath()
            );
        }

        private void increment() {
            productCount++;
        }

        private Long naverCategoryId() {
            return naverCategoryId;
        }

        private String naverCategoryCode() {
            return naverCategoryCode;
        }

        private String naverCategoryFullPath() {
            return naverCategoryFullPath;
        }

        private long productCount() {
            return productCount;
        }
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
