package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.*;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ProductExcelProcessingResult;
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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductExcelProcessingService {
    private final CategoryMatcherService categoryMatcherService;
    private final ProductExcelSheetProcessor productExcelSheetProcessor;
    private final ProductKeywordGenerator productKeywordGenerator;

    ProductExcelProcessingResult processExcel(
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
    private ProductExcelProcessingResult processExcel(
            InputStream inputStream,
            ProductExcelProcessingRequest request,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
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

            PreparedProductResults preparedResults = writeKeywordsAndResults(
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
            productExcelSheetProcessor.writeKeywordDetails(workbook, preparedResults.keywordDetails());

            progressUpdater.update(productRows.size(), productRows.size(), "결과 엑셀 생성 중");
            String filename = buildDownloadFilename(request.originalFilename());
            ExcelDownloadResult userResult = new ExcelDownloadResult(filename, writeWorkbook(workbook));
            if (request.includeSelectionDetails()) {
                ExcelDownloadResult adminResult = new ExcelDownloadResult(
                        buildAdminDownloadFilename(request.originalFilename()),
                        userResult.content()
                );
                return new ProductExcelProcessingResult(userResult, adminResult);
            }

            ProductExcelSheetContext adminSheetContext = productExcelSheetProcessor.prepareSheet(
                    workbook,
                    request.productNameColumn(),
                    request.categoryColumn(),
                    true
            );
            productExcelSheetProcessor.writeProductResultRows(
                    productRows,
                    myCategoryResults,
                    preparedResults.keywordCategories(),
                    preparedResults.keywordsByRow(),
                    adminSheetContext,
                    true
            );
            productExcelSheetProcessor.writeUnmatchedOrRejectedRatio(
                    adminSheetContext,
                    productRows,
                    myCategoryResults
            );
            ExcelDownloadResult adminResult = new ExcelDownloadResult(
                    buildAdminDownloadFilename(request.originalFilename()),
                    writeWorkbook(workbook)
            );
            return new ProductExcelProcessingResult(userResult, adminResult);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process excel file.");
        }
    }

    private byte[] writeWorkbook(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private Map<Integer, MyCategoryMatchResult> matchCategories(
            List<ProductExcelRow> productRows,
            Long userId,
            ProductExcelJobProgressUpdater progressUpdater
    ) {
        List<CategoryMatchProductRequest> products = productRows.stream()
                .map(productRow -> new CategoryMatchProductRequest(
                        productRow.rowId(), productRow.productName(), productRow.imageUrl()))
                .toList();
        int totalCount = products.size();
        progressUpdater.update(0, totalCount, "카테고리 검색 준비 중");
        long categoryStartedAt = System.nanoTime();
        Map<Integer, MyCategoryMatchResult> myCategoryResults = categoryMatcherService.findCategoryMatches(
                products,
                userId,
                processedCount -> progressUpdater.update(processedCount, totalCount, "카테고리 찾는 중")
        );
        progressUpdater.recordCategoryCompleted(elapsedMillis(categoryStartedAt));
        return myCategoryResults;
    }

    private PreparedProductResults writeKeywordsAndResults(
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
                                keywordCategories.get(productRow.rowId()),
                                myCategoryResults.getOrDefault(productRow.rowId(),
                                        MyCategoryMatchResult.noCategoryMatch()).imageAnalysis()
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
        return new PreparedProductResults(keywordDetails, keywordCategories, keywordsByRow);
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

    private String buildAdminDownloadFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input.xlsx"
                : originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "selection_details_" + baseName;
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record PreparedProductResults(
            List<KeywordDetailEntry> keywordDetails,
            Map<Integer, String> keywordCategories,
            Map<Integer, List<GeneratedKeyword>> keywordsByRow
    ) {
    }
}
