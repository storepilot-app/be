package com.be.keywordjob.service;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.dto.MyCategoryMatchStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.keywordjob.dto.ImageDownloadResponse;
import com.be.keywordjob.dto.ImageZipDownloadResult;
import com.be.keywordjob.keyword.CategoryTokenExtractor;
import com.be.keywordjob.keyword.KeywordCombinationTemplate;
import com.be.keywordjob.keyword.KeywordSynonymDictionary;
import com.be.keywordjob.keyword.ProductNameTokenExtractor;
import com.be.keywordjob.keyword.SimilarProductRepeatedPhraseExtractor;
import com.be.keywordjob.keyword.SimilarProductRepeatedPhraseExtractor.ProductSource;
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
public class KeywordExcelFillService {
    private static final int KEYWORD_COLUMN_INDEX = 11; // L
    private static final int MY_CATEGORY_COLUMN_INDEX = 19; // T
    private static final int NAVER_CATEGORY_COLUMN_INDEX = 20; // U
    private static final int TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX = 26; // AA
    private static final int TOP_NAVER_CATEGORIES_START_COLUMN_INDEX = 27; // AB
    private static final int TOP_NAVER_CATEGORIES_COUNT = 5;
    private static final int SELECTED_CATEGORY_COLUMN_INDEX = 32; // AG
    private static final int LLM_STATUS_COLUMN_INDEX = 33; // AH
    private static final int LEGACY_OUTPUT_START_COLUMN_INDEX = 34; // AI
    private static final int LEGACY_OUTPUT_END_COLUMN_INDEX = 38; // AM
    private static final int TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH = 35 * 256;
    private static final int TOP_NAVER_CATEGORY_COLUMN_WIDTH = 60 * 256;
    private static final int SELECTED_CATEGORY_COLUMN_WIDTH = 60 * 256;
    private static final int LLM_STATUS_COLUMN_WIDTH = 50 * 256;
    private static final int LLM_STATUS_DETAIL_MAX_LENGTH = 180;
    private static final int DEFAULT_KEYWORD_COUNT = 30;
    private static final int CATEGORY_BATCH_SIZE = 30;
    private static final String KEYWORD_HEADER = "키워드";
    private static final String MY_CATEGORY_HEADER = "마이카테";
    private static final String NAVER_CATEGORY_HEADER = "네이버카테";
    private static final String TOP_NAVER_PRODUCT_NAME_HEADER = "상품명";
    private static final String TOP_NAVER_CATEGORIES_HEADER_PREFIX = "유사상품-";
    private static final String SELECTED_CATEGORY_HEADER = "선택카테고리";
    private static final String LLM_STATUS_HEADER = "LLM상태";
    private static final String IMAGE_URL_COLUMN = "목록이미지1";
    private static final String PRODUCT_CODE_COLUMN = "상품코드";
    private static final String PRODUCT_NUMBER_COLUMN = "제품번호";
    private static final String NO_CATEGORY_MATCH = "매칭없음";
    private static final String NO_MY_CATEGORY_MAPPING = "마이카테 없음";
    private static final String NO_SELECTED_CATEGORY = "없음";

    private final CategoryMatcherService categoryMatcherService;
    private final KeywordJobUploadService keywordJobUploadService;
    private final CategoryTokenExtractor categoryTokenExtractor;
    private final KeywordCombinationTemplate keywordCombinationTemplate;
    private final KeywordSynonymDictionary keywordSynonymDictionary;
    private final ProductNameTokenExtractor productNameTokenExtractor;
    private final SimilarProductRepeatedPhraseExtractor similarProductRepeatedPhraseExtractor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public ExcelDownloadResult fillAndDownload(
            MultipartFile file,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            String userKey
    ) {
        keywordJobUploadService.validate(file, productNameColumn, keywordCount);

        try (InputStream inputStream = file.getInputStream()) {
            return fillAndDownload(
                    inputStream,
                    file.getOriginalFilename(),
                    productNameColumn,
                    categoryColumn,
                    keywordCount,
                    userKey,
                    CategoryJobProgressListener.NO_OP
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read excel file.");
        }
    }

    public ExcelDownloadResult fillAndDownload(
            Path filePath,
            String originalFilename,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            String userKey,
            CategoryJobProgressListener progressListener
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
            CategoryJobProgressListener progressListener
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
            clearLegacyOutputCells(headerRow);
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
            Map<Integer, MyCategoryMatchResult> myCategoryResults = findCategoriesInBatches(
                    products,
                    userKey,
                    progressListener
            );
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
                List<String> keywords = generateKeywords(
                        productName,
                        keywordCategory,
                        repeatedPhrases.getOrDefault(productRow.rowId(), List.of()),
                        resolvedKeywordCount
                );

                row.createCell(KEYWORD_COLUMN_INDEX).setCellValue(String.join(", ", keywords));
                row.createCell(MY_CATEGORY_COLUMN_INDEX).setCellValue(myCategory);
                writeNaverCategory(row, myCategoryResult);
                row.createCell(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX).setCellValue(productName);
                writeSimilarProducts(row, myCategoryResult, llmStatusCellStyles.selected());
                writeSelectedCategory(row, myCategoryResult);
                writeLlmStatus(row, myCategoryResult, llmStatusCellStyles);
                clearLegacyOutputCells(row);
            }
            writeUnmatchedOrRejectedRatio(sheet, productRows, myCategoryResults);

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
            CategoryJobProgressListener progressListener
    ) {
        Map<Integer, MyCategoryMatchResult> results = new HashMap<>();
        int totalCount = products.size();
        progressListener.onProgress(0, totalCount, "카테고리 검색 준비 중");

        for (int start = 0; start < totalCount; start += CATEGORY_BATCH_SIZE) {
            int end = Math.min(start + CATEGORY_BATCH_SIZE, totalCount);
            results.putAll(categoryMatcherService.findMyCategoryCodes(products.subList(start, end), userKey));
            progressListener.onProgress(end, totalCount, "카테고리 찾는 중");
        }
        return results;
    }

    private record ProductExcelRow(int rowId, Row row, String productName, String category) {
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

    public ImageDownloadResponse downloadImages(MultipartFile file, String imageOutputDir) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int imageUrlColumnIndex = findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN);
            int productCodeColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN);
            int productNumberColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN);
            Path imageDirectory = resolveImageDirectory(imageOutputDir);
            Files.createDirectories(imageDirectory);

            int savedCount = 0;
            int failedCount = 0;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                boolean saved = downloadImageIfPresent(
                        row,
                        formatter,
                        imageUrlColumnIndex,
                        productCodeColumnIndex,
                        productNumberColumnIndex,
                        imageDirectory,
                        rowIndex + 1
                );

                if (saved) {
                    savedCount++;
                } else {
                    failedCount++;
                }
            }

            return new ImageDownloadResponse(
                    savedCount,
                    failedCount,
                    imageDirectory.toString(),
                    "Images downloaded."
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process image download.");
        }
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

    private boolean downloadImageIfPresent(
            Row row,
            DataFormatter formatter,
            int imageUrlColumnIndex,
            int productCodeColumnIndex,
            int productNumberColumnIndex,
            Path imageDirectory,
            int excelRowNumber
    ) {
        String imageUrl = readCell(row, imageUrlColumnIndex, formatter);
        if (!isHttpUrl(imageUrl)) {
            return false;
        }

        String productCode = productCodeColumnIndex < 0 ? "" : readCell(row, productCodeColumnIndex, formatter);
        String productNumber = productNumberColumnIndex < 0 ? "" : readCell(row, productNumberColumnIndex, formatter);
        String filenameBase = !productCode.isBlank() ? productCode : (!productNumber.isBlank() ? productNumber : "row_" + excelRowNumber);
        Path targetPath = imageDirectory.resolve(safeFilename(filenameBase) + imageExtension(imageUrl));

        try {
            Files.write(targetPath, fetchImage(imageUrl));
            return true;
        } catch (Exception ignored) {
            return false;
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
    }

    private void applyTopNaverCategoryColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX, TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            sheet.setColumnWidth(TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + index, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
        sheet.setColumnWidth(SELECTED_CATEGORY_COLUMN_INDEX, SELECTED_CATEGORY_COLUMN_WIDTH);
        sheet.setColumnWidth(LLM_STATUS_COLUMN_INDEX, LLM_STATUS_COLUMN_WIDTH);
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
        clearLegacyOutputCells(summaryRow);
        summaryRow.createCell(SELECTED_CATEGORY_COLUMN_INDEX).setCellValue("못찾음/거절 비율");
        summaryRow.createCell(LLM_STATUS_COLUMN_INDEX).setCellValue(String.format(
                Locale.ROOT,
                "%d/%d (%.2f%%)",
                unmatchedOrRejectedCount,
                totalCount,
                ratio
        ));
    }

    private void clearLegacyOutputCells(Row row) {
        for (int columnIndex = LEGACY_OUTPUT_START_COLUMN_INDEX;
             columnIndex <= LEGACY_OUTPUT_END_COLUMN_INDEX;
             columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) {
                row.removeCell(cell);
            }
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

    private List<String> generateKeywords(
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

        List<String> candidates = keywordCombinationTemplate.generate(
                productTokens,
                categoryTokens,
                repeatedPhrases,
                keywordSynonymDictionary.findSynonyms(synonymSources)
        );
        return candidates.stream()
                .limit(keywordCount)
                .toList();
    }

    private Path resolveImageDirectory(String imageOutputDir) {
        if (imageOutputDir != null && !imageOutputDir.isBlank()) {
            return Path.of(imageOutputDir).toAbsolutePath().normalize();
        }
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("product-images");
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
