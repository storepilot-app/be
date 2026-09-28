package com.be.productexceljob.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.dto.ProductExcelProcessingResult;
import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import com.be.userusage.service.UserUsageService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.nio.file.Files;
import java.util.Optional;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ProductExcelJobServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void recordsUsageAfterExcelJobCompletion() throws Exception {
        ProductExcelJobRequestValidator validator = mock(ProductExcelJobRequestValidator.class);
        ProductExcelProcessingService processingService = mock(ProductExcelProcessingService.class);
        UserUsageService userUsageService = mock(UserUsageService.class);
        Executor directExecutor = Runnable::run;
        ProductExcelJobRepository repository = mock(ProductExcelJobRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            ProductExcelJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "jobId", 1L);
            when(repository.findByJobIdAndUserId(1L, 1L)).thenReturn(Optional.of(job));
            when(repository.findById(1L)).thenReturn(Optional.of(job));
            return job;
        });
        ProductExcelResultStorage storage = new ProductExcelResultStorage(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDirectory.toString());
        ProductExcelJobService service = new ProductExcelJobService(
                repository,
                validator,
                processingService,
                userUsageService,
                directExecutor,
                mock(PlatformTransactionManager.class),
                storage
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
        LocalDate usageDate = LocalDate.of(2026, 9, 8);
        when(validator.validate(any(), any(), any())).thenReturn(3);
        when(userUsageService.reserveCategoryProducts(1L, 3)).thenReturn(usageDate);
        when(processingService.processExcel(any(), any())).thenAnswer(invocation -> {
            ProductExcelJobProgressUpdater progressUpdater = invocation.getArgument(1);
            progressUpdater.update(3, 3, "결과 엑셀 생성 중");
            return new ProductExcelProcessingResult(
                    new ExcelDownloadResult("result.xlsx", new byte[]{1}),
                    new ExcelDownloadResult("admin-result.xlsx", new byte[]{2})
            );
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1}
        );

        service.createExcelJob(file, 1L, false);

        verify(userUsageService).completeCategoryKeywordJob(1L, usageDate, 3);
        ProductExcelJob job = repository.findByJobIdAndUserId(1L, 1L).orElseThrow();
        assertThat(job.getResultContent()).isNull();
        assertThat(Files.exists(job.getUploadedFilePath())).isFalse();
        assertThat(service.getExcelDownloadResult(1L, 1L).content()).containsExactly(1);
        assertThat(service.getExcelDownloadResult(1L, 1L).content()).containsExactly(1);
        assertThat(service.getAdminExcelDownloadResult(1L).content()).containsExactly(2);
        ReflectionTestUtils.setField(job, "resultExpiresAt", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.getExcelDownloadResult(1L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.JOB_RESULT_EXPIRED));
        assertThat(service.getExcelJobStatus(1L, 1L).resultExpired()).isTrue();
        assertThatThrownBy(() -> service.getExcelDownloadResult(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }
}
