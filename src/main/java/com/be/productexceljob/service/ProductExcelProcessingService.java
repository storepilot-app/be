package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.*;
import static com.be.productexceljob.excel.ProductImageDownloadLayout.*;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.dto.MyCategoryMatchStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ProductImageDownloadFailure;
import com.be.productexceljob.dto.ProductImageDownloadItem;
import com.be.productexceljob.dto.ProductImageDownloadPrepareResponse;
import com.be.productexceljob.excel.KeywordDetailSheetWriter;
import com.be.watermark.domain.WatermarkPosition;
import com.be.watermark.service.UserWatermarkService;
import com.be.watermark.service.UserWatermarkService.WatermarkImage;
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
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelProcessingService {
    private static final int PRODUCT_IMAGE_SIZE = 1000;
    private static final String PRODUCT_IMAGE_EXTENSION = ".jpg";
    private static final int MIN_TARGET_SIZE_PERCENT = 30;
    private static final int MAX_TARGET_SIZE_PERCENT = 100;
    private static final float MIN_JPEG_QUALITY = 0.1f;
    private static final float MAX_JPEG_QUALITY = 1.0f;
    private static final int JPEG_QUALITY_SEARCH_ITERATIONS = 8;
    private static final String NO_CATEGORY_MATCH = "매칭없음";
    private static final String NO_MY_CATEGORY_MAPPING = "마이카테 없음";
    private static final String NO_SELECTED_CATEGORY = "없음";

    private final CategoryMatcherService categoryMatcherService;
    private final CategoryTokenExtractor categoryTokenExtractor;
    private final KeywordCandidateRanker keywordCandidateRanker;
    private final KeywordCombinationTemplate keywordCombinationTemplate;
    private final KeywordDetailSheetWriter keywordDetailSheetWriter;
    private final KeywordSynonymDictionary keywordSynonymDictionary;
    private final ProductNameTokenExtractor productNameTokenExtractor;
    private final SimilarProductRepeatedPhraseExtractor similarProductRepeatedPhraseExtractor;
    private final UserWatermarkService userWatermarkService;
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
            Long userId,
            boolean includeSelectionDetails,
            ProductExcelProgressCallback progressCallback
    ) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return fillAndDownload(
                    inputStream,
                    originalFilename,
                    productNameColumn,
                    categoryColumn,
                    keywordCount,
                    userId,
                    includeSelectionDetails,
                    progressCallback
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read excel file.");
        }
    }

    // 실제 처리 로직
    private ExcelDownloadResult fillAndDownload(
            InputStream inputStream,
            String originalFilename,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            Long userId,
            boolean includeSelectionDetails,
            ProductExcelProgressCallback progressCallback
    ) {
        try (Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) //AutoCloseable 객체 try문이 끝나면 자동으로 닫힘
        {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            ProductExcelSheetContext sheetContext = prepareSheet(
                    workbook,
                    sheet,
                    headerRow,
                    productNameColumn,
                    categoryColumn,
                    includeSelectionDetails
            );

            int resolvedKeywordCount = keywordCount == null ? DEFAULT_KEYWORD_COUNT : keywordCount;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            List<ProductExcelRow> productRows = readProductRows(sheet, sheetContext, formatter);
            Map<Integer, MyCategoryMatchResult> myCategoryResults = matchCategories(
                    productRows,
                    userId,
                    progressCallback
            );
            List<KeywordDetailEntry> keywordDetails = writeKeywordsAndResults(
                    productRows,
                    myCategoryResults,
                    resolvedKeywordCount,
                    sheetContext,
                    includeSelectionDetails,
                    progressCallback
            );
            if (includeSelectionDetails) {
                writeUnmatchedOrRejectedRatio(sheet, productRows, myCategoryResults);
            }
            keywordDetailSheetWriter.write(workbook, keywordDetails);

            progressCallback.onProgress(productRows.size(), productRows.size(), "결과 엑셀 생성 중");
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
            Long userId,
            ProductExcelProgressCallback progressCallback
    ) {
        Map<Integer, MyCategoryMatchResult> results = new HashMap<>();
        int totalCount = products.size();
        progressCallback.onProgress(0, totalCount, "카테고리 검색 준비 중");

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
            progressCallback.onProgress(end, totalCount, "카테고리 찾는 중");
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
            ProductExcelProgressCallback progressCallback
    ) {
        List<CategoryMatchProductRequest> products = productRows.stream()
                .map(productRow -> new CategoryMatchProductRequest(productRow.rowId(), productRow.productName()))
                .toList();
        long categoryStartedAt = System.nanoTime();
        Map<Integer, MyCategoryMatchResult> myCategoryResults = findCategoriesInBatches(
                products,
                userId,
                progressCallback
        );
        progressCallback.onCategoryCompleted(elapsedMillis(categoryStartedAt));
        return myCategoryResults;
    }

    private List<KeywordDetailEntry> writeKeywordsAndResults(
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> myCategoryResults,
            int resolvedKeywordCount,
            ProductExcelSheetContext sheetContext,
            boolean includeSelectionDetails,
            ProductExcelProgressCallback progressCallback
    ) {
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
        List<KeywordDetailEntry> keywordDetails = writeProductResultRows(
                productRows,
                myCategoryResults,
                keywordCategories,
                repeatedPhrases,
                resolvedKeywordCount,
                sheetContext,
                includeSelectionDetails
        );
        progressCallback.onKeywordCompleted(elapsedMillis(keywordStartedAt));
        return keywordDetails;
    }

    private ProductExcelSheetContext prepareSheet(
            Workbook workbook,
            Sheet sheet,
            Row headerRow,
            String productNameColumn,
            String categoryColumn,
            boolean includeSelectionDetails
    ) {
        CellStyle selectedStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
        CellStyle rejectedStyle = createFillStyle(workbook, IndexedColors.ROSE);

        int productNameColumnIndex = findRequiredColumnIndex(headerRow, productNameColumn);
        int categoryColumnIndex = findOptionalColumnIndex(headerRow, categoryColumn);
        ensureHeader(headerRow, KEYWORD_COLUMN_INDEX, KEYWORD_HEADER);
        ensureHeader(headerRow, MY_CATEGORY_COLUMN_INDEX, MY_CATEGORY_HEADER);
        ensureHeader(headerRow, NAVER_CATEGORY_COLUMN_INDEX, NAVER_CATEGORY_HEADER);
        if (includeSelectionDetails) {
            ensureTopNaverCategoryHeaders(headerRow);
            applyTopNaverCategoryColumnWidths(sheet);
        } else {
            hideSelectionDetailColumns(sheet);
        }

        return new ProductExcelSheetContext(
                productNameColumnIndex,
                categoryColumnIndex,
                selectedStyle,
                rejectedStyle
        );
    }

    private List<ProductExcelRow> readProductRows(
            Sheet sheet,
            ProductExcelSheetContext sheetContext,
            DataFormatter formatter
    ) {
        List<ProductExcelRow> productRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String productName = readCell(row, sheetContext.productNameColumnIndex(), formatter);
            String category = sheetContext.categoryColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.categoryColumnIndex(), formatter);
            if (productName.isBlank()) {
                continue;
            }

            productRows.add(new ProductExcelRow(rowIndex, row, productName, category));
        }
        return productRows;
    }

    private List<KeywordDetailEntry> writeProductResultRows(
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> myCategoryResults,
            Map<Integer, String> keywordCategories,
            Map<Integer, List<String>> repeatedPhrases,
            int resolvedKeywordCount,
            ProductExcelSheetContext sheetContext,
            boolean includeSelectionDetails
    ) {
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
                    .map(this::removeKeywordSpaces)
                    .collect(java.util.stream.Collectors.joining(",")));
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
            if (includeSelectionDetails) {
                row.createCell(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX).setCellValue(productName);
                writeSimilarProducts(row, myCategoryResult, sheetContext.selectedStyle());
                writeSelectedCategory(row, myCategoryResult);
                writeLlmStatus(row, myCategoryResult, sheetContext.selectedStyle(), sheetContext.rejectedStyle());
                writeCategoryEmbeddingCandidates(row, myCategoryResult, sheetContext.selectedStyle());
            }
        }

        return keywordDetails;
    }

    private record ProductExcelSheetContext(
            int productNameColumnIndex,
            int categoryColumnIndex,
            CellStyle selectedStyle,
            CellStyle rejectedStyle
    ) {
    }

    private record ProductExcelRow(
            int rowId,
            Row row,
            String productName,
            String category
    ) {
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

    public ProductImageDownloadPrepareResponse prepareImageDownloads(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            ProductImageDownloadSheetContext sheetContext = prepareImageDownloadSheet(sheet);
            ProductImageDownloadRows rows = readImageDownloadRows(sheet, sheetContext);

            return new ProductImageDownloadPrepareResponse(
                    rows.images().size(),
                    rows.failures().size(),
                    rows.images(),
                    rows.failures()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read image download targets.");
        }
    }

    private ProductImageDownloadSheetContext prepareImageDownloadSheet(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
        }

        return new ProductImageDownloadSheetContext(
                findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN)
        );
    }

    private ProductImageDownloadRows readImageDownloadRows(
            Sheet sheet,
            ProductImageDownloadSheetContext sheetContext
    ) {
        Set<String> entryNames = new LinkedHashSet<>();
        List<ProductImageDownloadItem> images = new ArrayList<>();
        List<ProductImageDownloadFailure> failures = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String imageUrl = normalizeImageUrl(readCell(row, sheetContext.imageUrlColumnIndex(), formatter));
            String productCode = sheetContext.productCodeColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productCodeColumnIndex(), formatter);
            String productNumber = sheetContext.productNumberColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productNumberColumnIndex(), formatter);
            String filenameBase = !productNumber.isBlank()
                    ? productNumber
                    : (!productCode.isBlank() ? productCode : "row_" + (rowIndex + 1));
            if (!isHttpUrl(imageUrl)) {
                failures.add(new ProductImageDownloadFailure(
                        rowIndex + 1,
                        filenameBase,
                        imageUrl,
                        "이미지 URL이 비어 있거나 올바르지 않습니다."
                ));
                continue;
            }

            String entryName = uniqueEntryName(entryNames, safeFilename(filenameBase), PRODUCT_IMAGE_EXTENSION);
            images.add(new ProductImageDownloadItem(rowIndex + 1, filenameBase, entryName, imageUrl));
        }

        return new ProductImageDownloadRows(images, failures);
    }

    private record ProductImageDownloadSheetContext(
            int imageUrlColumnIndex,
            int productCodeColumnIndex,
            int productNumberColumnIndex
    ) {
    }

    private record ProductImageDownloadRows(
            List<ProductImageDownloadItem> images,
            List<ProductImageDownloadFailure> failures
    ) {
    }

    public byte[] downloadImage(
            String imageUrl,
            Integer targetSizePercent,
            Long userId,
            boolean applyWatermark
    ) {
        if (!isHttpUrl(imageUrl)) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 URL이 비어 있거나 올바르지 않습니다.");
        }
        if (targetSizePercent == null
                || targetSizePercent < MIN_TARGET_SIZE_PERCENT
                || targetSizePercent > MAX_TARGET_SIZE_PERCENT) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "목표 용량 비율은 30~100 사이여야 합니다.");
        }

        try {
            byte[] originalImage = fetchImage(imageUrl);
            BufferedImage resizedImage = resizeImageToSquare(originalImage);
            if (applyWatermark) {
                applyWatermark(resizedImage, userWatermarkService.getRequiredImage(userId));
            }
            long targetBytes = Math.max(1L, Math.round(originalImage.length * targetSizePercent / 100.0));
            return compressJpegToTarget(resizedImage, targetBytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드가 중단되었습니다.");
        }
    }

    public byte[] createImageFailureExcel(List<ProductImageDownloadFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "저장할 이미지 다운로드 실패 내역이 없습니다.");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("이미지 다운로드 실패");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("엑셀 행");
            headerRow.createCell(1).setCellValue("파일명");
            headerRow.createCell(2).setCellValue("이미지 URL");
            headerRow.createCell(3).setCellValue("실패 사유");

            for (int index = 0; index < failures.size(); index++) {
                ProductImageDownloadFailure failure = failures.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(failure.rowNumber());
                row.createCell(1).setCellValue(safeCellValue(failure.name()));
                row.createCell(2).setCellValue(safeCellValue(failure.url()));
                row.createCell(3).setCellValue(safeCellValue(failure.reason()));
            }

            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 25 * 256);
            sheet.setColumnWidth(2, 70 * 256);
            sheet.setColumnWidth(3, 50 * 256);
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드 실패 목록 엑셀을 생성하지 못했습니다.");
        }
    }

    private String safeCellValue(String value) {
        return value == null ? "" : value;
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

    private BufferedImage resizeImageToSquare(byte[] imageBytes) throws IOException {
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (sourceImage == null) {
            throw new IOException("지원하지 않는 이미지 형식입니다.");
        }

        BufferedImage resizedImage = new BufferedImage(
                PRODUCT_IMAGE_SIZE,
                PRODUCT_IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PRODUCT_IMAGE_SIZE, PRODUCT_IMAGE_SIZE);

            double scale = Math.min(
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getWidth(),
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getHeight()
            );
            int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
            int x = (PRODUCT_IMAGE_SIZE - width) / 2;
            int y = (PRODUCT_IMAGE_SIZE - height) / 2;
            graphics.drawImage(sourceImage, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }

        return resizedImage;
    }

    private void applyWatermark(BufferedImage productImage, WatermarkImage watermark) throws IOException {
        BufferedImage watermarkImage = ImageIO.read(new ByteArrayInputStream(watermark.content()));
        if (watermarkImage == null) {
            throw new IOException("저장된 워터마크 이미지를 읽지 못했습니다.");
        }

        int targetWidth = Math.max(1, productImage.getWidth() * watermark.sizePercent() / 100);
        int targetHeight = Math.max(1, (int) Math.round(
                targetWidth * watermarkImage.getHeight() / (double) watermarkImage.getWidth()
        ));
        if (targetHeight > productImage.getHeight()) {
            targetHeight = productImage.getHeight();
            targetWidth = Math.max(1, (int) Math.round(
                    targetHeight * watermarkImage.getWidth() / (double) watermarkImage.getHeight()
            ));
        }

        int margin = Math.max(10, productImage.getWidth() / 50);
        WatermarkCoordinates coordinates = watermarkCoordinates(
                productImage.getWidth(),
                productImage.getHeight(),
                targetWidth,
                targetHeight,
                margin,
                watermark.position()
        );

        Graphics2D graphics = productImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER,
                    watermark.opacity() / 100.0f
            ));
            graphics.drawImage(
                    watermarkImage,
                    coordinates.x(),
                    coordinates.y(),
                    targetWidth,
                    targetHeight,
                    null
            );
        } finally {
            graphics.dispose();
        }
    }

    private WatermarkCoordinates watermarkCoordinates(
            int imageWidth,
            int imageHeight,
            int watermarkWidth,
            int watermarkHeight,
            int margin,
            WatermarkPosition position
    ) {
        return switch (position) {
            case TOP_LEFT -> new WatermarkCoordinates(margin, margin);
            case TOP_RIGHT -> new WatermarkCoordinates(imageWidth - watermarkWidth - margin, margin);
            case CENTER -> new WatermarkCoordinates(
                    (imageWidth - watermarkWidth) / 2,
                    (imageHeight - watermarkHeight) / 2
            );
            case BOTTOM_LEFT -> new WatermarkCoordinates(margin, imageHeight - watermarkHeight - margin);
            case BOTTOM_RIGHT -> new WatermarkCoordinates(
                    imageWidth - watermarkWidth - margin,
                    imageHeight - watermarkHeight - margin
            );
        };
    }

    private record WatermarkCoordinates(int x, int y) {
    }

    private byte[] compressJpegToTarget(BufferedImage image, long targetBytes) throws IOException {
        byte[] highestQuality = writeJpeg(image, MAX_JPEG_QUALITY);
        if (highestQuality.length <= targetBytes) {
            return highestQuality;
        }

        byte[] lowestQuality = writeJpeg(image, MIN_JPEG_QUALITY);
        if (lowestQuality.length > targetBytes) {
            return lowestQuality;
        }

        byte[] bestResult = lowestQuality;
        float low = MIN_JPEG_QUALITY;
        float high = MAX_JPEG_QUALITY;
        for (int iteration = 0; iteration < JPEG_QUALITY_SEARCH_ITERATIONS; iteration++) {
            float quality = (low + high) / 2;
            byte[] candidate = writeJpeg(image, quality);
            if (candidate.length <= targetBytes) {
                bestResult = candidate;
                low = quality;
            } else {
                high = quality;
            }
        }
        return bestResult;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 이미지 인코더를 찾지 못했습니다.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
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
        sheet.setColumnHidden(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX, false);
        sheet.setColumnWidth(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX, TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            int columnIndex = TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + index;
            sheet.setColumnHidden(columnIndex, false);
            sheet.setColumnWidth(columnIndex, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
        sheet.setColumnHidden(SELECTED_CATEGORY_COLUMN_INDEX, false);
        sheet.setColumnWidth(SELECTED_CATEGORY_COLUMN_INDEX, SELECTED_CATEGORY_COLUMN_WIDTH);
        sheet.setColumnHidden(LLM_STATUS_COLUMN_INDEX, false);
        sheet.setColumnWidth(LLM_STATUS_COLUMN_INDEX, LLM_STATUS_COLUMN_WIDTH);
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            int columnIndex = CATEGORY_EMBEDDING_START_COLUMN_INDEX + index;
            sheet.setColumnHidden(columnIndex, false);
            sheet.setColumnWidth(columnIndex, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
    }

    private void hideSelectionDetailColumns(Sheet sheet) {
        for (int columnIndex = TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX;
             columnIndex < CATEGORY_EMBEDDING_START_COLUMN_INDEX + CATEGORY_EMBEDDING_COUNT;
             columnIndex++) {
            sheet.setColumnHidden(columnIndex, true);
        }
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
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

    private void writeLlmStatus(
            Row row,
            MyCategoryMatchResult result,
            CellStyle selectedStyle,
            CellStyle rejectedStyle
    ) {
        Cell cell = row.createCell(LLM_STATUS_COLUMN_INDEX);
        cell.setCellValue(formatLlmStatus(result.llmStatus(), result.llmStatusDetail()));
        if ("SELECTED".equals(result.llmStatus()) || "AUTO_SELECTED".equals(result.llmStatus())) {
            cell.setCellStyle(selectedStyle);
        } else if ("REJECTED".equals(result.llmStatus())) {
            cell.setCellStyle(rejectedStyle);
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

    private CellStyle createFillStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
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

    private String removeKeywordSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private record GeneratedKeyword(
            ScoredKeyword score,
            List<String> reasons
    ) {
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return "";
        }
        return imageUrl.trim().replace(" ", "%20");
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
