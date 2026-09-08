package com.be.productexceljob.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.productexceljob.dto.ExcelDownloadResult;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import com.be.userusage.service.UserUsageService;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ProductExcelJobServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void recordsUsageAfterExcelJobCompletion() {
        ProductExcelJobRequestValidator validator = mock(ProductExcelJobRequestValidator.class);
        ProductExcelProcessingService processingService = mock(ProductExcelProcessingService.class);
        UserUsageService userUsageService = mock(UserUsageService.class);
        Executor directExecutor = Runnable::run;
        ProductExcelJobService service = new ProductExcelJobService(
                new ProductExcelJobRepository(),
                validator,
                processingService,
                userUsageService,
                directExecutor
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
        when(processingService.processExcel(any(), any())).thenAnswer(invocation -> {
            ProductExcelJobProgressUpdater progressUpdater = invocation.getArgument(1);
            progressUpdater.update(3, 3, "결과 엑셀 생성 중");
            return new ExcelDownloadResult("result.xlsx", new byte[]{1});
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1}
        );

        service.createExcelJob(file, 1L, false);

        verify(userUsageService).recordCategoryKeywordJob(1L, 3);
    }
}
