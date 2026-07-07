package com.be.keywordjob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class KeywordExcelFileValidator {
    public void validate(MultipartFile file, String productNameColumn, Integer keywordCount) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Please upload an excel file.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !isExcelFilename(filename)) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Invalid excel file format.");
        }

        if (productNameColumn == null || productNameColumn.isBlank()) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "Product name column is required.");
        }

        if (keywordCount != null && (keywordCount < 1 || keywordCount > 50)) {
            throw new BusinessException(ErrorCode.KEYWORD_GENERATION_FAILED, "keywordCount must be between 1 and 50.");
        }
    }

    private boolean isExcelFilename(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }
}
