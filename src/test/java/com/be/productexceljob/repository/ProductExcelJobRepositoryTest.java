package com.be.productexceljob.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.service.ProductExcelJobProgressUpdater;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductExcelJobRepositoryTest {
    @Autowired
    private ProductExcelJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reloadsCompletedResultAndRestrictsAccessToOwner() {
        ProductExcelJob job = repository.saveAndFlush(newJob());
        Long jobId = job.getJobId();
        entityManager.clear();

        job.markProcessing();
        new ProductExcelJobProgressUpdater(job, repository).update(2, 3, "카테고리 찾는 중");
        repository.flush();
        entityManager.clear();
        ProductExcelJob progress = repository.findByJobIdAndUserId(jobId, 1L).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProductExcelJobStatus.PROCESSING);
        assertThat(progress.getProcessedCount()).isEqualTo(2);
        assertThat(progress.getUploadedFilePath()).isEqualTo(Path.of("uploads", "input.xlsx"));
        entityManager.clear();

        job.markCompleted("result.xlsx", new byte[]{1, 2, 3});
        repository.saveAndFlush(job);
        entityManager.clear();

        ProductExcelJob completed = repository.findByJobIdAndUserId(jobId, 1L).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ProductExcelJobStatus.COMPLETED);
        assertThat(completed.getResultContent()).containsExactly(1, 2, 3);
        assertThat(repository.findByJobIdAndUserId(jobId, 2L)).isEmpty();
        assertThat(repository.saveAndFlush(newJob()).getJobId()).isNotEqualTo(jobId);
    }

    private ProductExcelJob newJob() {
        return ProductExcelJob.register(1L, "input.xlsx", Path.of("uploads", "input.xlsx"),
                false, 3, LocalDate.of(2026, 9, 28));
    }
}
