package com.be.productexceljob.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductExcelJobTest {
    @Test
    void tracksBatchProgressAndCompletesAtOneHundredPercent() {
        ProductExcelJob job = ProductExcelJob.register(1L, 1L, "input.xlsx", Path.of("input.xlsx"), true);

        job.markProcessing();
        job.updateProgress(30, 100, "카테고리 찾는 중");
        job.recordCategoryElapsed(1_250);
        job.recordKeywordElapsed(340);

        assertEquals(ProductExcelJobStatus.PROCESSING, job.getStatus());
        assertEquals(30, job.getProcessedCount());
        assertEquals(100, job.getTotalCount());
        assertEquals(29, job.getProgress());
        assertEquals(1_250L, job.getCategoryElapsedMillis());
        assertEquals(340L, job.getKeywordElapsedMillis());

        job.markCompleted("result.xlsx", new byte[]{1});

        assertEquals(ProductExcelJobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("완료", job.getStage());
    }
}
