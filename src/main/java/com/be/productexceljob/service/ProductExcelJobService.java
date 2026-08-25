package com.be.productexceljob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.dto.ProductExcelJobCreateResponse;
import com.be.productexceljob.dto.ProductExcelJobStatusResponse;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelJobService {
    private static final String PRODUCT_NAME_COLUMN = "상품명";
    private static final int KEYWORD_COUNT = 30;

    private final ProductExcelJobRepository productExcelJobRepository;
    private final ProductExcelJobRequestValidator productExcelJobRequestValidator;
    private final ProductExcelProcessingService productExcelProcessingService;
    @Qualifier("productExcelJobExecutor")
    private final Executor productExcelJobExecutor;
    private final AtomicLong jobIdGenerator = new AtomicLong(1);

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public ProductExcelJobCreateResponse createExcelJob(MultipartFile file, Long userId, boolean includeSelectionDetails) {
        validateUserId(userId);
        productExcelJobRequestValidator.validate(file, PRODUCT_NAME_COLUMN, KEYWORD_COUNT);

        long jobId = jobIdGenerator.getAndIncrement();
        String filename = safeFilename(file.getOriginalFilename());
        Path jobDirectory = uploadRoot().resolve("product-excel-jobs").resolve(String.valueOf(jobId));
        Path targetPath = jobDirectory.resolve(filename).normalize();

        try {
            Files.createDirectories(jobDirectory);
            file.transferTo(targetPath);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 저장하지 못했습니다.");
        }

        ProductExcelJob job = productExcelJobRepository.save(ProductExcelJob.register(
                jobId,
                userId,
                filename,
                targetPath,
                includeSelectionDetails
        ));
        productExcelJobExecutor.execute(() -> process(job)); //process(job)을 지금 요청 스레드에서 바로 실행하지 말고 productExcelJobExecutor가 관리하는 백그라운드 스레드에서 실행
        return ProductExcelJobCreateResponse.from(job);
    }

    public ProductExcelJobStatusResponse status(long jobId, Long userId) {
        ProductExcelJob job = findJob(jobId, userId);
        return ProductExcelJobStatusResponse.from(job);
    }

    public ExcelDownloadResult download(long jobId, Long userId) {
        ProductExcelJob job = findJob(jobId, userId);
        if (job.getStatus() != ProductExcelJobStatus.COMPLETED
                || job.getResultFilename() == null
                || job.getResultContent() == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_COMPLETED, "아직 다운로드할 수 있는 결과가 없습니다.");
        }
        return new ExcelDownloadResult(job.getResultFilename(), job.getResultContent());
    }

    private void process(ProductExcelJob job) {
        job.markProcessing(); // 작업 상태를 처리 중으로 표시. 스레드 동작에 영향을 주지 않음
        try {
            ExcelDownloadResult result = productExcelProcessingService.fillAndDownload(
                    job.getUploadedFilePath(),
                    job.getOriginalFilename(),
                    PRODUCT_NAME_COLUMN,
                    "",
                    KEYWORD_COUNT,
                    job.getUserId(),
                    job.isIncludeSelectionDetails(),
                    progressCallback(job)
            );
            job.markCompleted(result.filename(), result.content()); // 작업 상태를 처리 완료로 표시. 스레드 동작에 영향을 주지 않음
        } catch (Exception error) {
            String message = error.getMessage() == null || error.getMessage().isBlank()
                    ? "카테고리 찾기 작업에 실패했습니다."
                    : error.getMessage();
            job.markFailed(message); // 작업 상태를 실패로 표시. 스레드 동작에 영향을 주지 않음
        } finally {
            deleteUploadedFile(job.getUploadedFilePath());
        }
    }

    private ProductExcelProgressCallback progressCallback(ProductExcelJob job) {
        return new ProductExcelProgressCallback() {
            @Override
            public void onProgress(int processedCount, int totalCount, String stage) {
                job.updateProgress(processedCount, totalCount, stage);
            }

            @Override
            public void onCategoryCompleted(long elapsedMillis) {
                job.recordCategoryElapsed(elapsedMillis);
            }

            @Override
            public void onKeywordCompleted(long elapsedMillis) {
                job.recordKeywordElapsed(elapsedMillis);
            }
        };
    }

    private ProductExcelJob findJob(long jobId, Long userId) {
        return productExcelJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND, "작업을 찾을 수 없습니다."));
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.CATEGORY_MATCHING_FAILED, "로그인이 필요합니다.");
        }
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

    private void deleteUploadedFile(Path uploadedFilePath) {
        if (uploadedFilePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(uploadedFilePath);
            Path jobDirectory = uploadedFilePath.getParent();
            if (jobDirectory != null && Files.isDirectory(jobDirectory)) {
                try (var children = Files.list(jobDirectory)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(jobDirectory);
                    }
                }
            }
        } catch (IOException error) {
            log.warn("업로드 임시 파일 삭제 실패: {}", uploadedFilePath, error);
        }
    }
}
