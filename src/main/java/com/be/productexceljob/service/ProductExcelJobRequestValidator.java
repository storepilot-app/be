package com.be.productexceljob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProductExcelJobRequestValidator {
    private static final int MIN_KEYWORD_COUNT = 1;
    private static final int MAX_KEYWORD_COUNT = 50;
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final String XLS_EXTENSION = ".xls";
    private static final int MAX_PRODUCTS_PER_JOB = 1_500;

    public int validate(MultipartFile file, String productNameColumn, Integer keywordCount) {
        validateFile(file);
        validateProductNameColumn(productNameColumn);
        validateKeywordCount(keywordCount);
        return countAndValidateProducts(file, productNameColumn);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 업로드해주세요.");
        }

        String filename = file.getOriginalFilename();
        if (!isExcelFilename(filename)) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일 형식이 올바르지 않습니다.");
        }
    }

    private void validateProductNameColumn(String productNameColumn) {
        if (productNameColumn == null || productNameColumn.isBlank()) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "상품명 컬럼이 필요합니다.");
        }
    }

    private void validateKeywordCount(Integer keywordCount) {
        if (keywordCount != null && (keywordCount < MIN_KEYWORD_COUNT || keywordCount > MAX_KEYWORD_COUNT)) {
            throw new BusinessException(
                    ErrorCode.KEYWORD_GENERATION_FAILED,
                    "키워드 개수는 %d개에서 %d개 사이여야 합니다.".formatted(MIN_KEYWORD_COUNT, MAX_KEYWORD_COUNT)
            );
        }
    }

    private boolean isExcelFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(XLSX_EXTENSION) || lower.endsWith(XLS_EXTENSION);
    }

    private int countAndValidateProducts(MultipartFile file, String productNameColumn) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 헤더 행이 비어 있습니다.");
            }

            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            int productNameColumnIndex = findProductNameColumnIndex(headerRow, productNameColumn, formatter);
            int productCount = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null
                        && row.getCell(productNameColumnIndex) != null
                        && !formatter.formatCellValue(row.getCell(productNameColumnIndex)).trim().isBlank()) {
                    productCount++;
                    if (productCount > MAX_PRODUCTS_PER_JOB) {
                        throw new BusinessException(
                                ErrorCode.PRODUCT_EXCEL_JOB_LIMIT_EXCEEDED,
                                "한 번에 처리할 수 있는 상품은 최대 1,500개입니다."
                        );
                    }
                }
            }
            return productCount;
        } catch (BusinessException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 읽지 못했습니다.");
        }
    }

    private int findProductNameColumnIndex(Row headerRow, String productNameColumn, DataFormatter formatter) {
        for (var cell : headerRow) {
            if (formatter.formatCellValue(cell).trim().equals(productNameColumn)) {
                return cell.getColumnIndex();
            }
        }
        throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "Column not found: " + productNameColumn);
    }
}
