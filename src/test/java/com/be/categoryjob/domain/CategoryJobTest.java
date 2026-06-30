package com.be.categoryjob.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CategoryJobTest {
    @Test
    void tracksBatchProgressAndCompletesAtOneHundredPercent() {
        CategoryJob job = new CategoryJob(1L, "user-a", "input.xlsx", Path.of("input.xlsx"));

        job.start();
        job.updateProgress(30, 100, "카테고리 찾는 중");

        assertEquals(CategoryJobStatus.PROCESSING, job.getStatus());
        assertEquals(30, job.getProcessedCount());
        assertEquals(100, job.getTotalCount());
        assertEquals(29, job.getProgress());

        job.complete("result.xlsx", new byte[]{1});

        assertEquals(CategoryJobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("완료", job.getStage());
    }
}
