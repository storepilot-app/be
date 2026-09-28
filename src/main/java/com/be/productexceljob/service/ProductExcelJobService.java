package com.be.productexceljob.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.dto.ProductExcelJobCreateResponse;
import com.be.productexceljob.dto.ProductExcelJobStatusResponse;
import com.be.productexceljob.dto.ProductExcelJobResultResponse;
import com.be.productexceljob.dto.AdminProductExcelJobResultResponse;
import com.be.productexceljob.dto.ProductExcelProcessingResult;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import com.be.userusage.service.UserUsageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
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
    private final UserUsageService userUsageService;
    @Qualifier("productExcelJobExecutor")
    private final Executor productExcelJobExecutor;
    private final PlatformTransactionManager transactionManager;
    private final ProductExcelResultStorage resultStorage;

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public ProductExcelJobCreateResponse createExcelJob(MultipartFile file, Long userId, boolean includeSelectionDetails) {
        validateUserId(userId);
        int productCount = productExcelJobRequestValidator.validate(file, PRODUCT_NAME_COLUMN, KEYWORD_COUNT);
        LocalDate usageDate = userUsageService.reserveCategoryProducts(userId, productCount);

        Long jobId = null;
        String filename = safeFilename(file.getOriginalFilename());
        Path jobDirectory = uploadRoot().resolve("product-excel-jobs").resolve(UUID.randomUUID().toString());
        Path targetPath = jobDirectory.resolve(filename).normalize();

        ProductExcelJob job;
        try {
            Files.createDirectories(jobDirectory);
            file.transferTo(targetPath);
            job = productExcelJobRepository.save(ProductExcelJob.register(
                    userId,
                    filename,
                    targetPath,
                    includeSelectionDetails,
                    productCount,
                    usageDate
            ));
            jobId = job.getJobId();
            productExcelJobExecutor.execute(() -> processExcelJob(job)); // 작업을 요청 스레드가 아닌 전용 Executor에서 실행
            return ProductExcelJobCreateResponse.from(job);
        } catch (IOException error) {
            releaseReservedProducts(userId, usageDate, productCount, jobId);
            deleteUploadedFile(targetPath);
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 저장하지 못했습니다.");
        } catch (RuntimeException error) {
            releaseReservedProducts(userId, usageDate, productCount, jobId);
            deleteUploadedFile(targetPath);
            throw error;
        }
    }

    public ProductExcelJobStatusResponse getExcelJobStatus(long jobId, Long userId) {
        ProductExcelJob job = findExcelJob(jobId, userId);
        return ProductExcelJobStatusResponse.from(job);
    }

    public List<ProductExcelJobResultResponse> getRecentExcelResults(Long userId) {
        validateUserId(userId);
        return productExcelJobRepository.findRecentCompletedResults(
                userId,
                Instant.now(),
                PageRequest.of(0, 100)
        );
    }

    public List<AdminProductExcelJobResultResponse> getRecentAdminExcelResults() {
        return productExcelJobRepository.findRecentCompletedAdminResults(
                Instant.now(),
                PageRequest.of(0, 100)
        );
    }

    public ExcelDownloadResult getExcelDownloadResult(long jobId, Long userId) {
        ProductExcelJob job = findExcelJob(jobId, userId);
        if (job.getStatus() != ProductExcelJobStatus.COMPLETED
                || job.getResultFilename() == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_COMPLETED, "아직 다운로드할 수 있는 결과가 없습니다.");
        }
        if (job.isResultExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.JOB_RESULT_EXPIRED,
                    "결과 파일의 보관 기간이 만료되었습니다. 다시 작업해 주세요.");
        }
        try {
            if (job.getResultFilePath() != null) {
                return new ExcelDownloadResult(job.getResultFilename(), resultStorage.read(job.getResultFilePath()));
            }
            if (job.getResultContent() != null) {
                return new ExcelDownloadResult(job.getResultFilename(), job.getResultContent());
            }
        } catch (IOException error) {
            log.warn("결과 엑셀 읽기 실패: jobId={}", jobId, error);
        }
        throw new BusinessException(ErrorCode.JOB_RESULT_UNAVAILABLE,
                "결과 파일을 읽을 수 없습니다. 잠시 후 다시 시도하거나 관리자에게 문의해 주세요.");
    }

    public ExcelDownloadResult getAdminExcelDownloadResult(long jobId) {
        ProductExcelJob job = productExcelJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND, "작업을 찾을 수 없습니다."));
        if (job.getStatus() != ProductExcelJobStatus.COMPLETED
                || job.getAdminResultFilename() == null
                || job.getAdminResultFilePath() == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_COMPLETED, "다운로드할 관리자용 결과가 없습니다.");
        }
        if (job.isResultExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.JOB_RESULT_EXPIRED,
                    "결과 파일의 보관 기간이 만료되었습니다.");
        }
        try {
            return new ExcelDownloadResult(
                    job.getAdminResultFilename(),
                    resultStorage.read(job.getAdminResultFilePath())
            );
        } catch (IOException error) {
            log.warn("관리자용 결과 엑셀 읽기 실패: jobId={}", jobId, error);
            throw new BusinessException(ErrorCode.JOB_RESULT_UNAVAILABLE,
                    "관리자용 결과 파일을 읽을 수 없습니다.");
        }
    }

    private void processExcelJob(ProductExcelJob job) {
        String savedResultPath = null;
        String savedAdminResultPath = null;
        try {
            job.markProcessing();
            productExcelJobRepository.save(job);
            ProductExcelJobProgressUpdater progressUpdater = new ProductExcelJobProgressUpdater(job, productExcelJobRepository);
            ProductExcelProcessingResult processingResult = productExcelProcessingService.processExcel(
                    new ProductExcelProcessingRequest(
                            job.getUploadedFilePath(),
                            job.getOriginalFilename(),
                            PRODUCT_NAME_COLUMN,
                            "",
                            KEYWORD_COUNT,
                            job.getUserId(),
                            job.isIncludeSelectionDetails()
                    ),
                    progressUpdater
            );
            ExcelDownloadResult result = processingResult.userResult();
            ExcelDownloadResult adminResult = processingResult.adminResult();
            savedResultPath = resultStorage.save(job.getJobId(), result.content());
            savedAdminResultPath = resultStorage.saveAdmin(job.getJobId(), adminResult.content());
            String resultPath = savedResultPath;
            String adminResultPath = savedAdminResultPath;
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                userUsageService.completeCategoryKeywordJob(
                        job.getUserId(),
                        job.getUsageDate(),
                        job.getProductCount()
                );
                job.markCompleted(
                        result.filename(),
                        resultPath,
                        adminResult.filename(),
                        adminResultPath,
                        Instant.now()
                );
                productExcelJobRepository.save(job);
            });
        } catch (Exception error) {
            if (savedResultPath != null) {
                try {
                    resultStorage.delete(savedResultPath);
                } catch (IOException cleanupError) {
                    log.warn("실패한 작업 결과 파일 삭제 실패: jobId={}", job.getJobId(), cleanupError);
                }
            }
            if (savedAdminResultPath != null) {
                try {
                    resultStorage.delete(savedAdminResultPath);
                } catch (IOException cleanupError) {
                    log.warn("실패한 관리자용 결과 파일 삭제 실패: jobId={}", job.getJobId(), cleanupError);
                }
            }
            releaseReservedProducts(job.getUserId(), job.getUsageDate(), job.getProductCount(), null);
            String message = error.getMessage() == null || error.getMessage().isBlank()
                    ? "카테고리 찾기 작업에 실패했습니다."
                    : error.getMessage();
            job.markFailed(message); // 작업 상태를 실패로 표시. 스레드 동작에 영향을 주지 않음
            productExcelJobRepository.save(job);
        } finally {
            deleteUploadedFile(job.getUploadedFilePath());
        }
    }

    private void releaseReservedProducts(Long userId, LocalDate usageDate, int productCount, Long jobId) {
        try {
            userUsageService.releaseCategoryProducts(userId, usageDate, productCount);
        } catch (RuntimeException error) {
            log.error("카테고리 및 키워드 작업 예약 사용량 반환 실패: jobId={}, userId={}", jobId, userId, error);
        }
        if (jobId != null) {
            productExcelJobRepository.deleteById(jobId);
        }
    }

    private ProductExcelJob findExcelJob(long jobId, Long userId) {
        return productExcelJobRepository.findByJobIdAndUserId(jobId, userId)
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
