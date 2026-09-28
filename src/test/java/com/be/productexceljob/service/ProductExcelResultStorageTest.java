package com.be.productexceljob.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductExcelResultStorageTest {
    @TempDir Path directory;
    @Autowired ProductExcelJobRepository repository;
    @Autowired EntityManager entityManager;
    private ProductExcelResultStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ProductExcelResultStorage(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", directory.toString());
    }

    @Test
    void expiresAtCompletionPlusSevenDaysAndKeepsHistory() throws Exception {
        Instant completedAt = Instant.parse("2026-09-28T00:00:00Z");
        ProductExcelJob job = repository.saveAndFlush(newJob());
        String path = storage.save(job.getJobId(), new byte[]{1, 2, 3});
        String adminPath = storage.saveAdmin(job.getJobId(), new byte[]{4, 5, 6});
        job.markCompleted("result.xlsx", path, "admin-result.xlsx", adminPath, completedAt);
        repository.saveAndFlush(job);
        entityManager.clear();
        Instant expiry = completedAt.plus(Duration.ofDays(7));

        storage.cleanupExpiredResults(expiry.minusSeconds(1));
        assertThat(storage.read(path)).containsExactly(1, 2, 3);
        storage.cleanupExpiredResults(expiry);
        entityManager.clear();

        assertThat(Files.exists(directory.resolve("product-excel-results").resolve(path))).isFalse();
        assertThat(Files.exists(directory.resolve("product-excel-results").resolve(adminPath))).isFalse();
        ProductExcelJob expired = repository.findById(job.getJobId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(ProductExcelJobStatus.COMPLETED);
        assertThat(expired.getResultDeletedAt()).isEqualTo(expiry);
        assertThat(expired.getProductCount()).isEqualTo(3);
        assertThat(expired.getResultFilePath()).isNull();
        storage.cleanupExpiredResults(expiry.plusSeconds(3600));
    }

    @Test
    void skipsPendingAndProcessingAndHandlesAlreadyMissingFile() {
        Instant now = Instant.now();
        ProductExcelJob pending = repository.saveAndFlush(newJob());
        ProductExcelJob processing = newJob();
        processing.markProcessing();
        repository.saveAndFlush(processing);
        ProductExcelJob completed = repository.saveAndFlush(newJob());
        completed.markCompleted(
                "result.xlsx",
                completed.getJobId() + "/result.xlsx",
                "admin-result.xlsx",
                completed.getJobId() + "/admin-result.xlsx",
                now.minus(Duration.ofDays(8))
        );
        repository.saveAndFlush(completed);
        entityManager.clear();

        storage.cleanupExpiredResults(now);
        entityManager.clear();
        assertThat(repository.findById(pending.getJobId()).orElseThrow().getStatus()).isEqualTo(ProductExcelJobStatus.PENDING);
        assertThat(repository.findById(processing.getJobId()).orElseThrow().getStatus()).isEqualTo(ProductExcelJobStatus.PROCESSING);
        assertThat(repository.findById(completed.getJobId()).orElseThrow().getResultDeletedAt()).isNotNull();
    }

    @Test
    void grantsLegacyResultsSevenDaysThenClearsBlob() {
        ProductExcelJob legacy = newJob();
        ReflectionTestUtils.setField(legacy, "status", ProductExcelJobStatus.COMPLETED);
        ReflectionTestUtils.setField(legacy, "resultContent", new byte[]{1});
        repository.saveAndFlush(legacy);
        entityManager.clear();
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        storage.cleanupExpiredResults(now);
        entityManager.clear();
        ProductExcelJob retained = repository.findById(legacy.getJobId()).orElseThrow();
        assertThat(retained.getResultExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        assertThat(retained.getResultContent()).containsExactly(1);
        entityManager.clear();
        storage.cleanupExpiredResults(now.plus(Duration.ofDays(7)));
        entityManager.clear();
        assertThat(repository.findById(legacy.getJobId()).orElseThrow().getResultContent()).isNull();
    }

    @Test
    void failedDeletionIsRetriedWithoutDeletingOtherFiles() throws Exception {
        Instant now = Instant.now();
        ProductExcelJob job = repository.saveAndFlush(newJob());
        String path = job.getJobId() + "/result.xlsx";
        job.markCompleted(
                "result.xlsx",
                path,
                "admin-result.xlsx",
                job.getJobId() + "/admin-result.xlsx",
                now.minus(Duration.ofDays(8))
        );
        repository.saveAndFlush(job);
        entityManager.clear();
        Path obstructed = directory.resolve("product-excel-results").resolve(path);
        Files.createDirectories(obstructed);
        Files.writeString(obstructed.resolve("keep.txt"), "keep");

        storage.cleanupExpiredResults(now);
        entityManager.clear();
        assertThat(repository.findById(job.getJobId()).orElseThrow().getResultDeletedAt()).isNull();
        assertThat(Files.readString(obstructed.resolve("keep.txt"))).isEqualTo("keep");
        assertThatThrownBy(() -> storage.delete("../outside.xlsx")).isInstanceOf(IllegalArgumentException.class);
    }

    private ProductExcelJob newJob() {
        return ProductExcelJob.register(1L, "input.xlsx", directory.resolve("input.xlsx"),
                false, 3, LocalDate.of(2026, 9, 28));
    }
}
