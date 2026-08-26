package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.*;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.keyword.KeywordDetailEntry;
import com.be.productexceljob.service.ProductExcelSheetProcessor.ProductExcelRow;
import com.be.productexceljob.service.ProductExcelSheetProcessor.ProductExcelSheetContext;
import com.be.productexceljob.service.ProductKeywordGenerator.GeneratedKeyword;
import com.be.productexceljob.service.ProductKeywordGenerator.ProductKeywordSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelProcessingService {
    private final CategoryMatcherService categoryMatcherService;
    private final ProductExcelSheetProcessor productExcelSheetProcessor;
    private final ProductKeywordGenerator productKeywordGenerator;

    @Value("${storepilot.category.batch-size:300}")
    private int categoryBatchSize;

    ExcelDownloadResult processExcel(
            ProductExcelProcessingRequest request,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        try (InputStream inputStream = Files.newInputStream(request.filePath())) {
            return processExcel(
                    inputStream,
                    request,
                    progressUpdater
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read excel file.");
        }
    }

    // 실제 처리 로직
    private ExcelDownloadResult processExcel(
            InputStream inputStream,
            ProductExcelProcessingRequest request,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        try (Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) //AutoCloseable 객체 try문이 끝나면 자동으로 닫힘
        {
            ProductExcelSheetContext sheetContext = productExcelSheetProcessor.prepareSheet(
                    workbook,
                    request.productNameColumn(),
                    request.categoryColumn(),
                    request.includeSelectionDetails()
            );

            int resolvedKeywordCount = request.keywordCount() == null
                    ? DEFAULT_KEYWORD_COUNT
                    : request.keywordCount();
            List<ProductExcelRow> productRows = productExcelSheetProcessor.readProductRows(sheetContext);
            Map<Integer, MyCategoryMatchResult> myCategoryResults = matchCategories(
                    productRows,
                    request.userId(),
                    progressUpdater
            );

            List<KeywordDetailEntry> keywordDetails = writeKeywordsAndResults(
                    productRows,
                    myCategoryResults,
                    resolvedKeywordCount,
                    sheetContext,
                    request.includeSelectionDetails(),
                    progressUpdater
            );

            if (request.includeSelectionDetails()) {
                productExcelSheetProcessor.writeUnmatchedOrRejectedRatio(
                        sheetContext,
                        productRows,
                        myCategoryResults
                );
            }
            productExcelSheetProcessor.writeKeywordDetails(workbook, keywordDetails);

            progressUpdater.update(productRows.size(), productRows.size(), "결과 엑셀 생성 중");
            workbook.write(outputStream);
            String filename = buildDownloadFilename(request.originalFilename());
            return new ExcelDownloadResult(filename, outputStream.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process excel file.");
        }
    }

    private Map<Integer, MyCategoryMatchResult> findCategoriesInBatches(
            List<CategoryMatchProductRequest> products,
            Long userId,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        Map<Integer, MyCategoryMatchResult> results = new HashMap<>();
        int totalCount = products.size();
        progressUpdater.update(0, totalCount, "카테고리 검색 준비 중");

        long allBatchesStartedAt = System.nanoTime();
        int batchNumber = 0;
        int safeBatchSize = Math.max(1, categoryBatchSize);
        for (int start = 0; start < totalCount; start += safeBatchSize) {
            batchNumber++;
            int end = Math.min(start + safeBatchSize, totalCount);
            long batchStartedAt = System.nanoTime();
            results.putAll(categoryMatcherService.findCategoryMatches(products.subList(start, end), userId));
            log.info(
                    "category_batch_timing batch={} batchSize={} processed={} total={} elapsedMs={}",
                    batchNumber,
                    end - start,
                    end,
                    totalCount,
                    elapsedMillis(batchStartedAt)
            );
            progressUpdater.update(end, totalCount, "카테고리 찾는 중");
        }
        log.info(
                "category_all_batches_timing batches={} products={} elapsedMs={}",
                batchNumber,
                totalCount,
                elapsedMillis(allBatchesStartedAt)
        );
        return results;
    }

    private Map<Integer, MyCategoryMatchResult> matchCategories(
            List<ProductExcelRow> productRows,
            Long userId,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        List<CategoryMatchProductRequest> products = productRows.stream()
                .map(productRow -> new CategoryMatchProductRequest(productRow.rowId(), productRow.productName()))
                .toList();
        long categoryStartedAt = System.nanoTime();
        Map<Integer, MyCategoryMatchResult> myCategoryResults = findCategoriesInBatches(
                products,
                userId,
                progressUpdater
        );
        progressUpdater.recordCategoryCompleted(elapsedMillis(categoryStartedAt));
        return myCategoryResults;
    }

    private List<KeywordDetailEntry> writeKeywordsAndResults(
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> myCategoryResults,
            int resolvedKeywordCount,
            ProductExcelSheetContext sheetContext,
            boolean includeSelectionDetails,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        long keywordStartedAt = System.nanoTime();
        Map<Integer, String> keywordCategories = resolveKeywordCategories(productRows, myCategoryResults);
        Map<Integer, List<GeneratedKeyword>> keywordsByRow = productKeywordGenerator.generate(
                productRows.stream()
                        .map(productRow -> new ProductKeywordSource(
                                productRow.rowId(),
                                productRow.productName(),
                                keywordCategories.get(productRow.rowId())
                        ))
                        .toList(),
                resolvedKeywordCount
        );
        List<KeywordDetailEntry> keywordDetails = productExcelSheetProcessor.writeProductResultRows(
                productRows,
                myCategoryResults,
                keywordCategories,
                keywordsByRow,
                sheetContext,
                includeSelectionDetails
        );
        progressUpdater.recordKeywordCompleted(elapsedMillis(keywordStartedAt));
        return keywordDetails;
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private Map<Integer, String> resolveKeywordCategories(
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> categoryResults
    ) {
        Map<Integer, String> categories = new HashMap<>();
        for (ProductExcelRow productRow : productRows) {
            MyCategoryMatchResult result = categoryResults.get(productRow.rowId());
            String category = result == null ? null : result.naverCategory();
            categories.put(
                    productRow.rowId(),
                    category == null || category.isBlank() ? productRow.category() : category
            );
        }
        return categories;
    }
    private String buildDownloadFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input.xlsx"
                : originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "keyword_result_" + baseName;
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
