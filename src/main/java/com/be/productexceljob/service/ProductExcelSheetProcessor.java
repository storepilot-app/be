package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.*;

import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.dto.MyCategoryMatchStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keyword.KeywordDetailEntry;
import com.be.productexceljob.excel.KeywordDetailSheetWriter;
import com.be.productexceljob.service.ProductKeywordGenerator.GeneratedKeyword;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductExcelSheetProcessor {
    private static final String NO_CATEGORY_MATCH = "매칭없음";
    private static final String NO_MY_CATEGORY_MAPPING = "마이카테 없음";
    private static final String NO_SELECTED_CATEGORY = "없음";

    private final KeywordDetailSheetWriter keywordDetailSheetWriter;

    ProductExcelSheetContext prepareSheet(
            Workbook workbook,
            String productNameColumn,
            String categoryColumn,
            boolean includeSelectionDetails
    ) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일에 시트가 없습니다.");
        }

        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 헤더 행이 비어 있습니다.");
        }

        CellStyle selectedStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
        CellStyle rejectedStyle = createFillStyle(workbook, IndexedColors.ROSE);
        int productNameColumnIndex = findRequiredColumnIndex(headerRow, productNameColumn);
        int categoryColumnIndex = findOptionalColumnIndex(headerRow, categoryColumn);
        int keywordColumnIndex = findOrAppendColumn(sheet, KEYWORD_HEADER);
        int myCategoryColumnIndex = findOrAppendColumn(sheet, MY_CATEGORY_HEADER);
        int naverCategoryColumnIndex = findOrAppendColumn(sheet, NAVER_CATEGORY_HEADER);
        int detailOffset = nextEmptyColumn(sheet) - TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX;
        if (includeSelectionDetails) {
            ensureTopNaverCategoryHeaders(headerRow, detailOffset);
            applyTopNaverCategoryColumnWidths(sheet, detailOffset);
        }

        return new ProductExcelSheetContext(
                sheet,
                productNameColumnIndex,
                categoryColumnIndex,
                keywordColumnIndex,
                myCategoryColumnIndex,
                naverCategoryColumnIndex,
                detailOffset,
                selectedStyle,
                rejectedStyle
        );
    }

    List<ProductExcelRow> readProductRows(ProductExcelSheetContext sheetContext) {
        List<ProductExcelRow> productRows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Sheet sheet = sheetContext.sheet();
        int imageColumn = findOptionalColumnIndex(sheet.getRow(0), "목록이미지1");
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

            String imageUrl = imageColumn < 0 ? null : readCell(row, imageColumn, formatter).trim();
            productRows.add(new ProductExcelRow(rowIndex, row, productName, category, imageUrl));
        }
        return productRows;
    }

    List<KeywordDetailEntry> writeProductResultRows(
            List<ProductExcelRow> productRows,
            Map<Integer, MyCategoryMatchResult> myCategoryResults,
            Map<Integer, String> keywordCategories,
            Map<Integer, List<GeneratedKeyword>> keywordsByRow,
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
            List<GeneratedKeyword> keywords = keywordsByRow.getOrDefault(productRow.rowId(), List.of());

            row.getCell(sheetContext.keywordColumnIndex(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(keywords.stream()
                    .map(keyword -> keyword.score().keyword())
                    .map(this::removeKeywordSpaces)
                    .collect(Collectors.joining(",")));
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
            row.getCell(sheetContext.myCategoryColumnIndex(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(myCategory);
            writeNaverCategory(row, myCategoryResult, sheetContext.naverCategoryColumnIndex());
            if (includeSelectionDetails) {
                int offset = sheetContext.detailOffset();
                row.createCell(TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX + offset).setCellValue(productName);
                writeSimilarProducts(row, myCategoryResult, sheetContext.selectedStyle(), offset);
                writeSelectedCategory(row, myCategoryResult, offset);
                writeLlmStatus(row, myCategoryResult, sheetContext.selectedStyle(), sheetContext.rejectedStyle(), offset);
                writeCategoryEmbeddingCandidates(row, myCategoryResult, sheetContext.selectedStyle(), offset);
            }
        }

        return keywordDetails;
    }

    void writeUnmatchedOrRejectedRatio(
            ProductExcelSheetContext sheetContext,
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
        Sheet sheet = sheetContext.sheet();
        int summaryRowIndex = productRows.stream()
                .mapToInt(ProductExcelRow::rowId)
                .max()
                .orElse(sheet.getLastRowNum()) + 1;
        Row summaryRow = sheet.getRow(summaryRowIndex);
        if (summaryRow == null) {
            summaryRow = sheet.createRow(summaryRowIndex);
        }
        int offset = sheetContext.detailOffset();
        summaryRow.createCell(SELECTED_CATEGORY_COLUMN_INDEX + offset).setCellValue("못찾음/거절 비율");
        summaryRow.createCell(LLM_STATUS_COLUMN_INDEX + offset).setCellValue(String.format(
                Locale.ROOT,
                "%d/%d (%.2f%%)",
                unmatchedOrRejectedCount,
                totalCount,
                ratio
        ));
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            summaryRow.createCell(CATEGORY_EMBEDDING_START_COLUMN_INDEX + offset + index).setCellValue("");
        }
    }

    void writeKeywordDetails(Workbook workbook, List<KeywordDetailEntry> keywordDetails) {
        keywordDetailSheetWriter.write(workbook, keywordDetails);
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

    private int nextEmptyColumn(Sheet sheet) {
        int column = 0;
        for (Row row : sheet) column = Math.max(column, row.getLastCellNum());
        return column;
    }

    private int findOrAppendColumn(Sheet sheet, String name) {
        Row header = sheet.getRow(0);
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        int found = -1;
        for (Cell cell : header) {
            if (formatter.formatCellValue(cell).replaceAll("\\s+", "").equals(name)) {
                if (found >= 0) throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "중복된 열 이름입니다: " + name);
                found = cell.getColumnIndex();
            }
        }
        if (found >= 0) return found;
        int column = nextEmptyColumn(sheet);
        ensureHeader(header, column, name);
        return column;
    }

    private void ensureTopNaverCategoryHeaders(Row headerRow, int offset) {
        ensureHeader(headerRow, (TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX + offset), TOP_NAVER_PRODUCT_NAME_HEADER);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            ensureHeader(
                    headerRow,
                    (TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + offset) + index,
                    TOP_NAVER_CATEGORIES_HEADER_PREFIX + (index + 1)
            );
        }
        ensureHeader(headerRow, (SELECTED_CATEGORY_COLUMN_INDEX + offset), SELECTED_CATEGORY_HEADER);
        ensureHeader(headerRow, (LLM_STATUS_COLUMN_INDEX + offset), LLM_STATUS_HEADER);
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            ensureHeader(
                    headerRow,
                    (CATEGORY_EMBEDDING_START_COLUMN_INDEX + offset) + index,
                    CATEGORY_EMBEDDING_HEADER_PREFIX + (index + 1)
            );
        }
    }

    private void applyTopNaverCategoryColumnWidths(Sheet sheet, int offset) {
        sheet.setColumnHidden((TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX + offset), false);
        sheet.setColumnWidth((TOP_NAVER_PRODUCT_NAME_COLUMN_INDEX + offset), TOP_NAVER_PRODUCT_NAME_COLUMN_WIDTH);
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            int columnIndex = (TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + offset) + index;
            sheet.setColumnHidden(columnIndex, false);
            sheet.setColumnWidth(columnIndex, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
        }
        sheet.setColumnHidden((SELECTED_CATEGORY_COLUMN_INDEX + offset), false);
        sheet.setColumnWidth((SELECTED_CATEGORY_COLUMN_INDEX + offset), SELECTED_CATEGORY_COLUMN_WIDTH);
        sheet.setColumnHidden((LLM_STATUS_COLUMN_INDEX + offset), false);
        sheet.setColumnWidth((LLM_STATUS_COLUMN_INDEX + offset), LLM_STATUS_COLUMN_WIDTH);
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            int columnIndex = (CATEGORY_EMBEDDING_START_COLUMN_INDEX + offset) + index;
            sheet.setColumnHidden(columnIndex, false);
            sheet.setColumnWidth(columnIndex, TOP_NAVER_CATEGORY_COLUMN_WIDTH);
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

    private void writeNaverCategory(Row row, MyCategoryMatchResult result, int columnIndex) {
        String naverCategory = result.naverCategory() == null || result.naverCategory().isBlank()
                ? NO_CATEGORY_MATCH
                : result.naverCategory();
        row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(naverCategory);
    }

    private void writeSimilarProducts(
            Row row,
            MyCategoryMatchResult result,
            CellStyle selectedCategoryStyle,
            int offset
    ) {
        List<CategoryMatchSimilarProduct> similarProducts = result.similarProducts();
        for (int index = 0; index < TOP_NAVER_CATEGORIES_COUNT; index++) {
            Cell cell = row.createCell((TOP_NAVER_CATEGORIES_START_COLUMN_INDEX + offset) + index);
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

    private void writeSelectedCategory(Row row, MyCategoryMatchResult result, int offset) {
        String value = result.naverCategory() == null || result.naverCategory().isBlank()
                ? NO_SELECTED_CATEGORY
                : result.naverCategory();
        row.createCell((SELECTED_CATEGORY_COLUMN_INDEX + offset)).setCellValue(value);
    }

    private void writeCategoryEmbeddingCandidates(
            Row row,
            MyCategoryMatchResult result,
            CellStyle selectedCategoryStyle,
            int offset
    ) {
        List<CategoryMatchCandidate> candidates = result.topNaverCategoryCandidates();
        for (int index = 0; index < CATEGORY_EMBEDDING_COUNT; index++) {
            Cell cell = row.createCell((CATEGORY_EMBEDDING_START_COLUMN_INDEX + offset) + index);
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
            CellStyle rejectedStyle,
            int offset
    ) {
        Cell cell = row.createCell((LLM_STATUS_COLUMN_INDEX + offset));
        cell.setCellValue(formatLlmStatus(result.llmStatus(), result.llmStatusDetail()));
        if ("SELECTED".equals(result.llmStatus()) || "AUTO_SELECTED".equals(result.llmStatus())) {
            cell.setCellStyle(selectedStyle);
        } else if ("REJECTED".equals(result.llmStatus())) {
            cell.setCellStyle(rejectedStyle);
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

    private String removeKeywordSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    record ProductExcelSheetContext(
            Sheet sheet,
            int productNameColumnIndex,
            int categoryColumnIndex,
            int keywordColumnIndex,
            int myCategoryColumnIndex,
            int naverCategoryColumnIndex,
            int detailOffset,
            CellStyle selectedStyle,
            CellStyle rejectedStyle
    ) {
    }

    record ProductExcelRow(
            int rowId,
            Row row,
            String productName,
            String category,
            String imageUrl
    ) {
        ProductExcelRow(int rowId, Row row, String productName, String category) {
            this(rowId, row, productName, category, null);
        }
    }
}
