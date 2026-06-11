package com.be.keywordjob.service;

import com.be.global.BusinessException;
import com.be.global.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.be.keywordjob.domain.KeywordJob;
import com.be.keywordjob.repository.KeywordJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KeywordJobUploadService {
    private final KeywordJobRepository keywordJobRepository;
    private final AtomicLong jobIdGenerator = new AtomicLong(1);
    private final Path uploadRoot;

    public KeywordJobUploadService(
            KeywordJobRepository keywordJobRepository,
            @Value("${storepilot.upload-dir:uploads}") String uploadDir
    ) {
        this.keywordJobRepository = keywordJobRepository;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public KeywordJob upload(
            MultipartFile file,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount
    ) {
        validate(file, productNameColumn, categoryColumn, keywordCount);

        long jobId = jobIdGenerator.getAndIncrement();
        String filename = safeFilename(file.getOriginalFilename());
        Path jobDir = uploadRoot.resolve("keyword-jobs").resolve(String.valueOf(jobId));
        Path targetPath = jobDir.resolve(filename).normalize();

        try {
            Files.createDirectories(jobDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 저장하지 못했습니다.");
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

    private void validate(MultipartFile file, String productNameColumn, String categoryColumn, Integer keywordCount) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 업로드해주세요.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !isExcelFilename(filename)) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일 형식이 올바르지 않습니다.");
        }

        if (productNameColumn == null || productNameColumn.isBlank()) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "상품명 컬럼명을 입력해주세요.");
        }

        if (categoryColumn == null || categoryColumn.isBlank()) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "네이버 카테고리 컬럼명을 입력해주세요.");
        }

        if (keywordCount != null && (keywordCount < 1 || keywordCount > 50)) {
            throw new BusinessException(ErrorCode.KEYWORD_GENERATION_FAILED, "keywordCount는 1~50 사이여야 합니다.");
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
}
