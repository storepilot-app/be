package com.be.keywordjob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keywordjob.domain.KeywordJob;
import com.be.keywordjob.repository.KeywordJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KeywordJobUploadService {
    private final KeywordJobRepository keywordJobRepository;
    private final AtomicLong jobIdGenerator = new AtomicLong(1);

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public KeywordJob upload(
            MultipartFile file,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount
    ) {
        validate(file, productNameColumn, keywordCount);

        long jobId = jobIdGenerator.getAndIncrement();
        String filename = safeFilename(file.getOriginalFilename());
        Path jobDir = uploadRoot().resolve("keyword-jobs").resolve(String.valueOf(jobId));
        Path targetPath = jobDir.resolve(filename).normalize();

        try {
            Files.createDirectories(jobDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to save excel file.");
        }

        int resolvedKeywordCount = keywordCount == null ? 30 : keywordCount;
        KeywordJob job = new KeywordJob(
                jobId,
                filename,
                productNameColumn,
                categoryColumn,
                resolvedKeywordCount,
                targetPath
        );
        return keywordJobRepository.save(job);
    }

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

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "input.xlsx";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }
}
