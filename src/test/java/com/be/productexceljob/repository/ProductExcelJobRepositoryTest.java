package com.be.productexceljob.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.service.ProductExcelJobProgressUpdater;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

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

        job.markCompleted(
                "result.xlsx",
                jobId + "/result.xlsx",
                "admin-result.xlsx",
                jobId + "/admin-result.xlsx",
                Instant.now()
        );
        repository.saveAndFlush(job);
        entityManager.clear();

        ProductExcelJob completed = repository.findByJobIdAndUserId(jobId, 1L).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ProductExcelJobStatus.COMPLETED);
        assertThat(completed.getResultContent()).isNull();
        assertThat(completed.getResultFilePath()).isEqualTo(jobId + "/result.xlsx");
        assertThat(repository.findByJobIdAndUserId(jobId, 2L)).isEmpty();
        var results = repository.findRecentCompletedResults(1L, Instant.now(), PageRequest.of(0, 100));
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.jobId()).isEqualTo(jobId);
            assertThat(result.filename()).isEqualTo("result.xlsx");
            assertThat(result.productCount()).isEqualTo(3);
            assertThat(result.resultExpired()).isFalse();
        });
        assertThat(repository.findRecentCompletedResults(2L, Instant.now(), PageRequest.of(0, 100))).isEmpty();
        assertThat(repository.saveAndFlush(newJob()).getJobId()).isNotEqualTo(jobId);
    }

    private ProductExcelJob newJob() {
        return ProductExcelJob.register(1L, "input.xlsx", Path.of("uploads", "input.xlsx"),
                false, 3, LocalDate.of(2026, 9, 28));
    }
}
