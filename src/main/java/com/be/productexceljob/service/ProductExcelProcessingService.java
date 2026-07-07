package com.be.productexceljob.service;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.dto.MyCategoryMatchStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ImageZipDownloadResult;
import com.be.productexceljob.excel.KeywordDetailSheetWriter;
import com.be.keyword.CategoryTokenExtractor;
import com.be.keyword.KeywordCandidateRanker;
import com.be.keyword.KeywordCandidateRanker.ScoredKeyword;
import com.be.keyword.KeywordCombinationTemplate;
import com.be.keyword.KeywordDetailEntry;
import com.be.keyword.KeywordSynonymDictionary;
import com.be.keyword.KeywordSynonymDictionary.SynonymExpansion;
import com.be.keyword.ProductNameTokenExtractor;
import com.be.keyword.SimilarProductRepeatedPhraseExtractor;
import com.be.keyword.SimilarProductRepeatedPhraseExtractor.ProductSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelProcessingService {
    private static final int KEYWORD_COLUMN_INDEX = 11; // L
    private static final int MY_CATEGORY_COLUMN_INDEX = 19; // T
    private static final int NAVER_CATEGORY_COLUMN_INDEX = 20; // U
    private static final int TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX = 26; // AA
    private static final int TOP_NAVER_CATEGORIES_START_COLUMN_INDEX = 27; // AB
    private static final int TOP_NAVER_CATEGORIES_COUNT = 5;
    private static final int SELECTED_CATEGORY_COLUMN_INDEX = 32; // AG
    private static final int LLM_STATUS_COLUMN_INDEX = 33; // AH
    private static final int CATEGORY_EMBEDDING_START_COLUMN_INDEX = 34; // AI
    private static final int CATEGORY_EMBEDDING_COUNT = 5;
    private static final int TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH = 35 * 256;
    private static final int TOP_NAVER_CATEGORY_COLUMN_WIDTH = 60 * 256;
    private static final int SELECTED_CATEGORY_COLUMN_WIDTH = 60 * 256;
    private static final int LLM_STATUS_COLUMN_WIDTH = 50 * 256;
    private static final int LLM_STATUS_DETAIL_MAX_LENGTH = 180;
    private static final int DEFAULT_KEYWORD_COUNT = 30;
    private static final String KEYWORD_HEADER = "키워드";
    private static final String MY_CATEGORY_HEADER = "마이카테";
    private static final String NAVER_CATEGORY_HEADER = "네이버카테";
    private static final String TOP_NAVER_PRODUCT_NAME_HEADER = "상품명";
    private static final String TOP_NAVER_CATEGORIES_HEADER_PREFIX = "유사상품-";
    private static final String SELECTED_CATEGORY_HEADER = "선택카테고리";
    private static final String LLM_STATUS_HEADER = "LLM상태";
    private static final String CATEGORY_EMBEDDING_HEADER_PREFIX = "카테고리검색-";
    private static final String IMAGE_URL_COLUMN = "목록이미지1";
    private static final String PRODUCT_CODE_COLUMN = "상품코드";
    private static final String PRODUCT_NUMBER_COLUMN = "제품번호";
    private static final String NO_CATEGORY_MATCH = "매칭없음";
    private static final String NO_MY_CATEGORY_MAPPING = "마이카테 없음";
    private static final String NO_SELECTED_CATEGORY = "없음";

    private final CategoryMatcherService categoryMatcherService;
    private final KeywordExcelFileValidator keywordExcelFileValidator;
    private final CategoryTokenExtractor categoryTokenExtractor;
    private final KeywordCandidateRanker keywordCandidateRanker;
    private final KeywordCombinationTemplate keywordCombinationTemplate;
    private final KeywordDetailSheetWriter keywordDetailSheetWriter;
    private final KeywordSynonymDictionary keywordSynonymDictionary;
    private final ProductNameTokenExtractor productNameTokenExtractor;
    private final SimilarProductRepeatedPhraseExtractor similarProductRepeatedPhraseExtractor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${storepilot.category.batch-size:300}")
    private int categoryBatchSize;

    public ExcelDownloadResult fillAndDownload(
            Path filePath,
            String originalFilename,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            String userKey,
            ProductExcelJobProgressListener progressListener
    ) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return fillAndDownload(
                    inputStream,
                    originalFilename,
                    productNameColumn,
                    categoryColumn,
                    keywordCount,
                    userKey,
                    progressListener
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read excel file.");
        }
    }

    private ExcelDownloadResult fillAndDownload(
            InputStream inputStream,
            String originalFilename,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            String userKey,
            ProductExcelJobProgressListener progressListener
    ) {
        try (Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }
            LlmStatusCellStyles llmStatusCellStyles = createLlmStatusCellStyles(workbook);

            int productNameColumnIndex = findRequiredColumnIndex(headerRow, productNameColumn);
            int categoryColumnIndex = findOptionalColumnIndex(headerRow, categoryColumn);
            ensureHeader(headerRow, KEYWORD_COLUMN_INDEX, KEYWORD_HEADER);
            ensureHeader(headerRow, MY_CATEGORY_COLUMN_INDEX, MY_CATEGORY_HEADER);
            ensureHeader(headerRow, NAVER_CATEGORY_COLUMN_INDEX, NAVER_CATEGORY_HEADER);
            ensureTopNaverCategoryHeaders(headerRow);
            applyTopNaverCategoryColumnWidths(sheet);

            int resolvedKeywordCount = keywordCount == null ? DEFAULT_KEYWORD_COUNT : keywordCount;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            List<ProductExcelRow> productRows = new ArrayList<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String productName = readCell(row, productNameColumnIndex, formatter);
                String category = categoryColumnIndex < 0 ? "" : readCell(row, categoryColumnIndex, formatter);
                if (productName.isBlank()) {
                    continue;
                }

                productRows.add(new ProductExcelRow(rowIndex, row, productName, category));
            }

            List<CategoryMatchProductRequest> products = productRows.stream()
                    .map(productRow -> new CategoryMatchProductRequest(productRow.rowId(), productRow.productName()))
                    .toList();
            long categoryStartedAt = System.nanoTime();
            Map<Integer, MyCategoryMatchResult> myCategoryResults = findCategoriesInBatches(
                    products,
                    userKey,
                    progressListener
            );
            progressListener.onCategoryCompleted(elapsedMillis(categoryStartedAt));

            long keywordStartedAt = System.nanoTime();
            Map<Integer, String> keywordCategories = resolveKeywordCategories(productRows, myCategoryResults);
            Map<Integer, List<String>> repeatedPhrases = similarProductRepeatedPhraseExtractor.extract(
                    productRows.stream()
                            .map(productRow -> new ProductSource(
                                    productRow.rowId(),
                                    productRow.productName(),
                                    keywordCategories.get(productRow.rowId())
                            ))
                            .toList()
            );
            List<KeywordDetailEntry> keywordDetails = new ArrayList<>();

            for (ProductExcelRow productRow : productRows) {
                Row row = productRow.row();
                String productName = productRow.productName();
                String category = productRow.category();
                MyCategoryMatchResult myCategoryResult = myCategoryResults.getOrDefault(
                        productRow.rowId(),
                        MyCategoryMatchResult.noCategoryMatch()
                );
                String myCategory = resolveMyCategory(myCategoryResult);
                String keywordCategory = keywordCategories.getOrDefault(productRow.rowId(), category);
                List<GeneratedKeyword> keywords = generateKeywords(
                        productName,
                        keywordCategory,
                        repeatedPhrases.getOrDefault(productRow.rowId(), List.of()),
                        resolvedKeywordCount
                );

                row.createCell(KEYWORD_COLUMN_INDEX).setCellValue(keywords.stream()
                        .map(keyword -> keyword.score().keyword())
                        .collect(java.util.stream.Collectors.joining(", ")));
                for (int index = 0; index < keywords.size(); index++) {
                    GeneratedKeyword keyword = keywords.get(index);
                    keywordDetails.add(new KeywordDetailEntry(
                            productRow.rowId() + 1,
                            productName,
                            keywordCategory,
                            index + 1,
                            keyword.score(),
                            keyword.reasons()
                    ));
                }
                row.createCell(MY_CATEGORY_COLUMN_INDEX).setCellValue(myCategory);
                writeNaverCategory(row, myCategoryResult);
                row.createCell(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX).setCellValue(productName);
                writeSimilarProducts(row, myCategoryResult, llmStatusCellStyles.selected());
                writeSelectedCategory(row, myCategoryResult);
                writeLlmStatus(row, myCategoryResult, llmStatusCellStyles);
                writeCategoryEmbeddingCandidates(row, myCategoryResult, llmStatusCellStyles.selected());
            }
            writeUnmatchedOrRejectedRatio(sheet, productRows, myCategoryResults);
            keywordDetailSheetWriter.write(workbook, keywordDetails);
            progressListener.onKeywordCompleted(elapsedMillis(keywordStartedAt));

            progressListener.onProgress(productRows.size(), productRows.size(), "결과 엑셀 생성 중");
            workbook.write(outputStream);
            String filename = buildDownloadFilename(originalFilename);
            return new ExcelDownloadResult(filename, outputStream.toByteArray());
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process excel file.");
        }
    }

    private Map<Integer, MyCategoryMatchResult> findCategoriesInBatches(
            List<CategoryMatchProductRequest> products,
            String userKey,
            ProductExcelJobProgressListener progressListener
    ) {
        Map<Integer, MyCategoryMatchResult> results = new HashMap<>();
        int totalCount = products.size();
        progressListener.onProgress(0, totalCount, "카테고리 검색 준비 중");

        long allBatchesStartedAt = System.nanoTime();
        int batchNumber = 0;
        int safeBatchSize = Math.max(1, categoryBatchSize);
        for (int start = 0; start < totalCount; start += safeBatchSize) {
            batchNumber++;
            int end = Math.min(start + safeBatchSize, totalCount);
            long batchStartedAt = System.nanoTime();
            results.putAll(categoryMatcherService.findMyCategoryCodes(products.subList(start, end), userKey));
            log.info(
                    "category_batch_timing batch={} batchSize={} processed={} total={} elapsedMs={}",
                    batchNumber,
                    end - start,
                    end,
                    totalCount,
                    elapsedMillis(batchStartedAt)
            );
            progressListener.onProgress(end, totalCount, "카테고리 찾는 중");
        }
        log.info(
                "category_all_batches_timing batches={} products={} elapsedMs={}",
                batchNumber,
                totalCount,
                elapsedMillis(allBatchesStartedAt)
        );
        return results;
    }

    private record ProductExcelRow(int rowId, Row row, String productName, String category) {
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

    public ImageZipDownloadResult downloadImagesAsZip(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int imageUrlColumnIndex = findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN);
            int productCodeColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN);
            int productNumberColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN);

            int savedCount = 0;
            int failedCount = 0;
            Set<String> entryNames = new LinkedHashSet<>();
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String imageUrl = readCell(row, imageUrlColumnIndex, formatter);
                if (!isHttpUrl(imageUrl)) {
                    failedCount++;
                    continue;
                }

                String productCode = productCodeColumnIndex < 0 ? "" : readCell(row, productCodeColumnIndex, formatter);
                String productNumber = productNumberColumnIndex < 0 ? "" : readCell(row, productNumberColumnIndex, formatter);
                String filenameBase = !productCode.isBlank() ? productCode : (!productNumber.isBlank() ? productNumber : "row_" + (rowIndex + 1));
                String entryName = uniqueEntryName(entryNames, safeFilename(filenameBase), imageExtension(imageUrl));

                try {
                    byte[] imageBytes = fetchImage(imageUrl);
                    ZipEntry entry = new ZipEntry(entryName);
                    zipOutputStream.putNextEntry(entry);
                    zipOutputStream.write(imageBytes);
                    zipOutputStream.closeEntry();
                    savedCount++;
                } catch (Exception ignored) {
                    failedCount++;
                }
            }

            zipOutputStream.finish();
            return new ImageZipDownloadResult(
                    buildImageZipFilename(file.getOriginalFilename()),
                    outputStream.toByteArray(),
                    savedCount,
                    failedCount
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process image zip download.");
        }
    }

    private byte[] fetchImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "StorePilot/1.0")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Image request failed with status " + response.statusCode());
        }
        return response.body();
    }

    private int findRequiredColumnIndex(Row headerRow, String columnName) {
        int index = findOptionalColumnIndex(headerRow, columnName);
        if (index < 0) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "Column not found: " + columnName);
        }
        return index;
    }

    private int findOptionalColumnIndex(Row headerRow, String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return -1;
        }

        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim();
            if (value.equals(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private void ensureHeader(Row headerRow, int columnIndex, String value) {
        Cell cell = headerRow.getCell(columnIndex);
        if (cell == null) {
            cell = headerRow.createCell(columnIndex);
        }
        cell.setCellValue(value);
    }

    private void ensureTopNaverCategoryHeaders(Row headerRow) {
        ensureHeader(headerRow, TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX, TOP_NAVER_PRODUCT_NAME_HEADER);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            ensureHeader(
                    headerRow,
                    TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + index,
                    TOP_NAVER_CATEGORIES_HEADER_PREFIX + (index + 1)
            );
        }
        ensureHeader(headerRow, SELECTED_CATEGORY_COLUMN_INDEX, SELECTED_CATEGORY_HEADER);
        ensureHeader(headerRow, LLM_STATUS_COLUMN_INDEX, LLM_STATUS_HEADER);
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            ensureHeader(
                    headerRow,
                    CATEGORY_EMBEDDING_START_COLUMN_INDEX + index,
                    CATEGORY_EMBEDDING_HEADER_PREFIX + (index + 1)
            );
        }
    }

    private void applyTopNaverCategoryColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX, TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            sheet.setColumnWidth(TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + index, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
        sheet.setColumnWidth(SELECTED_CATEGORY_COLUMN_INDEX, SELECTED_CATEGORY_COLUMN_WIDTH);
        sheet.setColumnWidth(LLM_STATUS_COLUMN_INDEX, LLM_STATUS_COLUMN_WIDTH);
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            sheet.setColumnWidth(CATEGORY_EMBEDDING_START_COLUMN_INDEX + index, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String inferMyCategory(String productName, String category) {
        if (category != null && !category.isBlank()) {
            String[] parts = category.split(">");
            String last = parts[parts.length - 1].trim();
            if (!last.isBlank()) {
                return last;
            }
        }

        String text = productName.toLowerCase(Locale.ROOT);
        if (text.contains("엽서")) return "엽서";
        if (text.contains("다이어리")) return "다이어리";
        if (text.contains("가계부")) return "가계부";
        if (text.contains("바인더")) return "바인더";
        if (text.contains("키보드")) return "키보드";
        if (text.contains("스티커") || text.contains("씰")) return "스티커";
        return "기타";
    }

    private String resolveMyCategory(MyCategoryMatchResult result) {
        if (result.status() == MyCategoryMatchStatus.MATCHED) {
            return result.myCategoryCode();
        }
        if (result.status() == MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING) {
            return NO_MY_CATEGORY_MAPPING;
        }
        return NO_CATEGORY_MATCH;
    }

    private void writeNaverCategory(Row row, MyCategoryMatchResult result) {
        String naverCategory = result.naverCategory() == null || result.naverCategory().isBlank()
                ? NO_CATEGORY_MATCH
                : result.naverCategory();
        row.createCell(NAVER_CATEGORY_COLUMN_INDEX).setCellValue(naverCategory);
    }

    private void writeSimilarProducts(
            Row row,
            MyCategoryMatchResult result,
            CellStyle selectedCategoryStyle
    ) {
        List<CategoryMatchSimilarProduct> similarProducts = result.similarProducts();
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            Cell cell = row.createCell(TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + index);
            if (similarProducts.isEmpty() && index == 0) {
                cell.setCellValue("유사상품 없음");
                continue;
            }
            if (index >= similarProducts.size()) {
                cell.setCellValue("");
                continue;
            }
            CategoryMatchSimilarProduct similarProduct = similarProducts.get(index);
            cell.setCellValue(formatSimilarProduct(similarProduct));
            if ("SELECTED".equals(result.llmStatus())
                    && result.naverCategory() != null
                    && result.naverCategory().equals(similarProduct.fullPath())) {
                cell.setCellStyle(selectedCategoryStyle);
            }
        }
    }

    private void writeSelectedCategory(Row row, MyCategoryMatchResult result) {
        String value = result.naverCategory() == null || result.naverCategory().isBlank()
                ? NO_SELECTED_CATEGORY
                : result.naverCategory();
        row.createCell(SELECTED_CATEGORY_COLUMN_INDEX).setCellValue(value);
    }

    private void writeCategoryEmbeddingCandidates(
            Row row,
            MyCategoryMatchResult result,
            CellStyle selectedCategoryStyle
    ) {
        List<CategoryMatchCandidate> candidates = result.topNaverCategoryCandidates();
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            Cell cell = row.createCell(CATEGORY_EMBEDDING_START_COLUMN_INDEX + index);
            if (index >= candidates.size()) {
                cell.setCellValue("");
                continue;
            }
            CategoryMatchCandidate candidate = candidates.get(index);
            cell.setCellValue(String.format(Locale.ROOT, "%s (%.4f)", candidate.fullPath(), candidate.score()));
            if (("SELECTED".equals(result.llmStatus()) || "AUTO_SELECTED".equals(result.llmStatus()))
                    && result.naverCategory() != null
                    && result.naverCategory().equals(candidate.fullPath())) {
                cell.setCellStyle(selectedCategoryStyle);
            }
        }
    }

    private void writeLlmStatus(Row row, MyCategoryMatchResult result, LlmStatusCellStyles styles) {
        Cell cell = row.createCell(LLM_STATUS_COLUMN_INDEX);
        cell.setCellValue(formatLlmStatus(result.llmStatus(), result.llmStatusDetail()));
        if ("SELECTED".equals(result.llmStatus()) || "AUTO_SELECTED".equals(result.llmStatus())) {
            cell.setCellStyle(styles.selected());
        } else if ("REJECTED".equals(result.llmStatus())) {
            cell.setCellStyle(styles.rejected());
        }
    }

    private void writeUnmatchedOrRejectedRatio(
            Sheet sheet,
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> myCategoryResults
    ) {
        if (productRows.isEmpty()) {
            return;
        }

        long unmatchedOrRejectedCount = productRows.stream()
                .map(productRow -> myCategoryResults.get(productRow.rowId()))
                .filter(result -> result == null
                        || result.status() == MyCategoryMatchStatus.NO_CATEGORY_MATCH
                        || "REJECTED".equals(result.llmStatus()))
                .count();
        int totalCount = productRows.size();
        double ratio = (double) unmatchedOrRejectedCount * 100 / totalCount;
        int summaryRowIndex = productRows.stream()
                .mapToInt(ProductExcelRow::rowId)
                .max()
                .orElse(sheet.getLastRowNum()) + 1;
        Row summaryRow = sheet.getRow(summaryRowIndex);
        if (summaryRow == null) {
            summaryRow = sheet.createRow(summaryRowIndex);
        }
        summaryRow.createCell(SELECTED_CATEGORY_COLUMN_INDEX).setCellValue("못찾음/거절 비율");
        summaryRow.createCell(LLM_STATUS_COLUMN_INDEX).setCellValue(String.format(
                Locale.ROOT,
                "%d/%d (%.2f%%)",
                unmatchedOrRejectedCount,
                totalCount,
                ratio
        ));
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            summaryRow.createCell(CATEGORY_EMBEDDING_START_COLUMN_INDEX + index).setCellValue("");
        }
    }

    private String formatLlmStatus(String llmStatus, String llmStatusDetail) {
        String status;
        if (llmStatus == null || llmStatus.isBlank()) {
            status = "호출안함";
        } else {
            status = switch (llmStatus) {
                case "SELECTED" -> "선택됨";
                case "REJECTED" -> "거절됨";
                case "FAILED" -> "호출실패";
                case "SKIPPED" -> "호출안함";
                case "AUTO_SELECTED" -> "자동선택";
                case "NO_SIMILAR_PRODUCTS" -> "유사상품 없음";
                default -> llmStatus;
            };
        }

        if (llmStatusDetail == null || llmStatusDetail.isBlank()) {
            return status;
        }
        return status + ": " + abbreviate(llmStatusDetail, LLM_STATUS_DETAIL_MAX_LENGTH);
    }

    private String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private LlmStatusCellStyles createLlmStatusCellStyles(Workbook workbook) {
        CellStyle selected = workbook.createCellStyle();
        selected.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        selected.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle rejected = workbook.createCellStyle();
        rejected.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        rejected.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return new LlmStatusCellStyles(selected, rejected);
    }

    private record LlmStatusCellStyles(CellStyle selected, CellStyle rejected) {
    }

    private String formatSimilarProduct(CategoryMatchSimilarProduct product) {
        return String.format(
                Locale.ROOT,
                "%s | %s (%.4f)",
                product.productName(),
                product.fullPath(),
                product.similarity()
        );
    }

    private List<GeneratedKeyword> generateKeywords(
            String productName,
            String category,
            List<String> repeatedPhrases,
            int keywordCount
    ) {
        List<String> productTokens = productNameTokenExtractor.extract(productName);
        List<String> categoryTokens = categoryTokenExtractor.extract(category);
        List<String> synonymSources = new ArrayList<>();
        synonymSources.addAll(productTokens);
        synonymSources.addAll(categoryTokens);
        synonymSources.addAll(repeatedPhrases);
        List<SynonymExpansion> synonymExpansions = keywordSynonymDictionary.findExpansions(synonymSources);
        List<String> synonyms = synonymExpansions.stream()
                .map(SynonymExpansion::keyword)
                .toList();

        List<String> candidates = keywordCombinationTemplate.generate(
                productTokens,
                categoryTokens,
                repeatedPhrases,
                synonyms
        );
        return keywordCandidateRanker.rank(
                        candidates,
                        productTokens,
                        categoryTokens,
                        repeatedPhrases,
                        synonyms
                ).stream()
                .limit(keywordCount)
                .map(score -> new GeneratedKeyword(
                        score,
                        resolveKeywordReasons(
                                score.keyword(),
                                productTokens,
                                categoryTokens,
                                repeatedPhrases,
                                synonymExpansions
                        )
                ))
                .toList();
    }

    private List<String> resolveKeywordReasons(
            String keyword,
            List<String> productTokens,
            List<String> categoryTokens,
            List<String> repeatedPhrases,
            List<SynonymExpansion> synonymExpansions
    ) {
        Set<String> reasons = new LinkedHashSet<>();
        if (containsKeyword(categoryTokens, keyword)) {
            reasons.add("카테고리 핵심어");
        }
        if (containsKeyword(repeatedPhrases, keyword)) {
            reasons.add("유사상품 반복 표현");
        }
        synonymExpansions.stream()
                .filter(expansion -> sameKeyword(expansion.keyword(), keyword))
                .map(expansion -> "동의어 치환: " + expansion.sourceTerm() + " → " + expansion.keyword())
                .forEach(reasons::add);
        if (containsKeyword(productTokens, keyword)) {
            reasons.add("상품명 토큰");
        }
        if (isProductTokenCombination(keyword, productTokens)) {
            reasons.add("상품명 토큰 조합");
        }
        if (isProductCategoryCombination(keyword, productTokens, categoryTokens)) {
            reasons.add("상품명 + 카테고리 조합");
        }
        if (reasons.isEmpty()) {
            reasons.add("조합 템플릿");
        }
        return List.copyOf(reasons);
    }

    private boolean containsKeyword(List<String> values, String keyword) {
        return values.stream().anyMatch(value -> sameKeyword(value, keyword));
    }

    private boolean isProductTokenCombination(String keyword, List<String> productTokens) {
        if (productTokens.size() >= 2 && sameKeyword(String.join("", productTokens), keyword)) {
            return true;
        }
        for (int index = 0; index + 1 < productTokens.size(); index++) {
            if (sameKeyword(productTokens.get(index) + productTokens.get(index + 1), keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductCategoryCombination(
            String keyword,
            List<String> productTokens,
            List<String> categoryTokens
    ) {
        if (categoryTokens.isEmpty()) {
            return false;
        }
        String primaryCategory = categoryTokens.getFirst();
        for (int index = 0; index < productTokens.size(); index++) {
            if (sameKeyword(combineWithCategory(List.of(productTokens.get(index)), primaryCategory), keyword)) {
                return true;
            }
            if (index + 1 < productTokens.size()
                    && sameKeyword(
                    combineWithCategory(productTokens.subList(index, index + 2), primaryCategory),
                    keyword
            )) {
                return true;
            }
        }
        return false;
    }

    private String combineWithCategory(List<String> tokens, String category) {
        StringBuilder prefix = new StringBuilder();
        String normalizedCategory = normalizeKeyword(category);
        for (String token : tokens) {
            String normalizedToken = normalizeKeyword(token);
            if (!normalizedCategory.contains(normalizedToken) && !normalizedToken.contains(normalizedCategory)) {
                prefix.append(token);
            }
        }
        return prefix.isEmpty() ? category : prefix + category;
    }

    private boolean sameKeyword(String first, String second) {
        return normalizeKeyword(first).equals(normalizeKeyword(second));
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record GeneratedKeyword(ScoredKeyword score, List<String> reasons) {
    }

    private String imageExtension(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            String extension = path.substring(dotIndex).toLowerCase(Locale.ROOT);
            if (extension.matches("\\.(jpg|jpeg|png|webp|gif)")) {
                return extension;
            }
        }
        return ".jpg";
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String uniqueEntryName(Set<String> entryNames, String filenameBase, String extension) {
        String normalizedBase = filenameBase == null || filenameBase.isBlank() ? "image" : filenameBase;
        String entryName = normalizedBase + extension;
        int sequence = 2;
        while (entryNames.contains(entryName)) {
            entryName = normalizedBase + "_" + sequence + extension;
            sequence++;
        }
        entryNames.add(entryName);
        return entryName;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String buildDownloadFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input.xlsx"
                : originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "keyword_result_" + baseName;
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String buildImageZipFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input"
                : originalFilename.replaceAll("\\.(xlsx|xls)$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "product_images_" + baseName + ".zip";
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Please upload an excel file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Invalid excel file format.");
        }
    }
}
