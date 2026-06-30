package com.be.categoryjob.service;

import com.be.categoryjob.domain.CategoryJob;
import com.be.categoryjob.domain.CategoryJobStatus;
import com.be.categoryjob.dto.CategoryJobCreateResponse;
import com.be.categoryjob.dto.CategoryJobStatusResponse;
import com.be.categoryjob.repository.CategoryJobRepository;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.keywordjob.service.KeywordExcelFillService;
import com.be.keywordjob.service.KeywordJobUploadService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CategoryJobService {
    private static final String PRODUCT_NAME_COLUMN = "상품명";
    private static final int KEYWORD_COUNT = 30;

    private final CategoryJobRepository categoryJobRepository;
    private final KeywordJobUploadService keywordJobUploadService;
    private final KeywordExcelFillService keywordExcelFillService;
    @Qualifier("categoryJobExecutor")
    private final Executor categoryJobExecutor;
    private final AtomicLong jobIdGenerator = new AtomicLong(1);

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public CategoryJobService(
            CategoryJobRepository categoryJobRepository,
            KeywordJobUploadService keywordJobUploadService,
            KeywordExcelFillService keywordExcelFillService,
            @Qualifier("categoryJobExecutor") Executor categoryJobExecutor
    ) {
        this.categoryJobRepository = categoryJobRepository;
        this.keywordJobUploadService = keywordJobUploadService;
        this.keywordExcelFillService = keywordExcelFillService;
        this.categoryJobExecutor = categoryJobExecutor;
    }

    public CategoryJobCreateResponse create(MultipartFile file, String userKey) {
        String normalizedUserKey = required(userKey, "사용자 식별자를 입력해주세요.");
        keywordJobUploadService.validate(file, PRODUCT_NAME_COLUMN, KEYWORD_COUNT);

        long jobId = jobIdGenerator.getAndIncrement();
        String filename = safeFilename(file.getOriginalFilename());
        Path jobDirectory = uploadRoot().resolve("category-jobs").resolve(String.valueOf(jobId));
        Path targetPath = jobDirectory.resolve(filename).normalize();

        try {
            Files.createDirectories(jobDirectory);
            file.transferTo(targetPath);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 저장하지 못했습니다.");
        }

        CategoryJob job = categoryJobRepository.save(new CategoryJob(
                jobId,
                normalizedUserKey,
                filename,
                targetPath
        ));
        categoryJobExecutor.execute(() -> process(job));
        return new CategoryJobCreateResponse(jobId, job.getStatus(), job.getMessage());
    }

    public CategoryJobStatusResponse status(long jobId) {
        CategoryJob job = findJob(jobId);
        return new CategoryJobStatusResponse(
                job.getJobId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getProcessedCount(),
                job.getProgress(),
                job.getStage(),
                job.getMessage()
        );
    }

    public ExcelDownloadResult download(long jobId) {
        CategoryJob job = findJob(jobId);
        if (job.getStatus() != CategoryJobStatus.COMPLETED
                || job.getResultFilename() == null
                || job.getResultContent() == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_COMPLETED, "아직 다운로드할 수 있는 결과가 없습니다.");
        }
        return new ExcelDownloadResult(job.getResultFilename(), job.getResultContent());
    }

    private void process(CategoryJob job) {
        job.start();
        try {
            ExcelDownloadResult result = keywordExcelFillService.fillAndDownload(
                    job.getUploadedFilePath(),
                    job.getOriginalFilename(),
                    PRODUCT_NAME_COLUMN,
                    "",
                    KEYWORD_COUNT,
                    job.getUserKey(),
                    job::updateProgress
            );
            job.complete(result.filename(), result.content());
        } catch (Exception error) {
            String message = error.getMessage() == null || error.getMessage().isBlank()
                    ? "카테고리 찾기 작업에 실패했습니다."
                    : error.getMessage();
            job.fail(message);
        }
    }

    private CategoryJob findJob(long jobId) {
        return categoryJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND, "작업을 찾을 수 없습니다."));
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, message);
        }
        return value.trim();
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
