package com.be.productexceljob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProductExcelJobRequestValidator {
    private static final int MIN_KEYWORD_COUNT = 1;
    private static final int MAX_KEYWORD_COUNT = 50;
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final String XLS_EXTENSION = ".xls";

    public void validate(MultipartFile file, String productNameColumn, Integer keywordCount) {
        validateFile(file);
        validateProductNameColumn(productNameColumn);
        validateKeywordCount(keywordCount);
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
}
