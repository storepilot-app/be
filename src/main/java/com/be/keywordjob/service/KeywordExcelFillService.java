package com.be.keywordjob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keywordjob.dto.ExcelDownloadResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KeywordExcelFillService {
    private static final int KEYWORD_COLUMN_INDEX = 11; // L
    private static final int MY_CATEGORY_COLUMN_INDEX = 19; // T
    private static final int DEFAULT_KEYWORD_COUNT = 30;

    private final KeywordJobUploadService keywordJobUploadService;

    public ExcelDownloadResult fillAndDownload(
            MultipartFile file,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount
    ) {
        keywordJobUploadService.validate(file, productNameColumn, keywordCount);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int productNameColumnIndex = findRequiredColumnIndex(headerRow, productNameColumn);
            int categoryColumnIndex = findOptionalColumnIndex(headerRow, categoryColumn);
            ensureHeader(headerRow, KEYWORD_COLUMN_INDEX, "키워드");
            ensureHeader(headerRow, MY_CATEGORY_COLUMN_INDEX, "마이카테");

            int resolvedKeywordCount = keywordCount == null ? DEFAULT_KEYWORD_COUNT : keywordCount;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

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

                String myCategory = inferMyCategory(productName, category);
                List<String> keywords = generateKeywords(productName, category, myCategory, resolvedKeywordCount);

                row.createCell(KEYWORD_COLUMN_INDEX).setCellValue(String.join(", ", keywords));
                row.createCell(MY_CATEGORY_COLUMN_INDEX).setCellValue(myCategory);
            }

            workbook.write(outputStream);
            String filename = buildDownloadFilename(file.getOriginalFilename());
            return new ExcelDownloadResult(filename, outputStream.toByteArray());
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process excel file.");
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

    private List<String> generateKeywords(String productName, String category, String myCategory, int keywordCount) {
        Set<String> keywords = new LinkedHashSet<>();
        List<String> tokens = tokenize(productName);

        addKeyword(keywords, myCategory);
        addKeyword(keywords, myCategory + "추천");
        addKeyword(keywords, myCategory + "선물");
        addKeyword(keywords, myCategory + "문구");
        addKeyword(keywords, "감성" + myCategory);
        addKeyword(keywords, "귀여운" + myCategory);
        addKeyword(keywords, "디자인" + myCategory);
        addKeyword(keywords, "학생" + myCategory);
        addKeyword(keywords, "사무용" + myCategory);

        for (String token : tokens) {
            addKeyword(keywords, token + myCategory);
            addKeyword(keywords, token);
        }

        if (category != null && !category.isBlank()) {
            for (String token : tokenize(category)) {
                addKeyword(keywords, token + myCategory);
            }
        }

        return keywords.stream()
                .limit(keywordCount)
                .toList();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] rawTokens = text.split("[\\s_/(),\\[\\]-]+");
        List<String> tokens = new ArrayList<>();
        for (String rawToken : rawTokens) {
            String token = rawToken.replaceAll("[^가-힣A-Za-z0-9]", "").trim();
            if (token.length() >= 2 && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void addKeyword(Set<String> keywords, String keyword) {
        if (keyword == null) {
            return;
        }
        String cleaned = keyword.replaceAll("\\s+", "").trim();
        if (cleaned.length() >= 2 && cleaned.length() <= 20) {
            keywords.add(cleaned);
        }
    }

    private String buildDownloadFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input.xlsx"
                : originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "keyword_result_" + baseName;
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
