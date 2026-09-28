package com.be.productexceljob.service;

import com.be.productexceljob.domain.ProductExcelJobStatus;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelResultStorage {
    private final ProductExcelJobRepository repository;

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public String save(long jobId, byte[] content) throws IOException {
        String relativePath = jobId + "/result.xlsx";
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "result-", ".tmp");
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return relativePath;
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    public void delete(String relativePath) throws IOException {
        Path target = resolve(relativePath);
        Files.deleteIfExists(target);
        // 폴더는 재귀 삭제하지 않고, 비어 있는 경우에만 제거한다.
        if (Files.isDirectory(target.getParent())) {
            try (var children = Files.list(target.getParent())) {
                if (children.findAny().isEmpty()) {
                    Files.deleteIfExists(target.getParent());
                }
            }
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void cleanupExpiredResults() {
        cleanupExpiredResults(Instant.now());
    }

    void cleanupExpiredResults(Instant now) {
        // 기존 DB 결과는 완료 시각이 없으므로 최초 정리 실행부터 7일을 보장한다.
        repository.initializeLegacyResultExpiry(now.plus(Duration.ofDays(7)));
        for (Long jobId : repository.findExpiredResultIds(now)) {
            try {
                var job = repository.findById(jobId).orElse(null);
                if (job == null || job.getStatus() != ProductExcelJobStatus.COMPLETED
                        || !job.isResultExpired(now) || job.getResultDeletedAt() != null) {
                    continue;
                }
                if (job.getResultFilePath() != null) {
                    delete(job.getResultFilePath());
                }
                repository.markResultDeleted(jobId, now);
            } catch (Exception error) {
                log.warn("만료된 엑셀 결과 삭제 실패: jobId={}", jobId, error);
            }
        }
    }

    private Path resolve(String relativePath) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("product-excel-results");
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("결과 파일 경로가 저장 디렉터리를 벗어났습니다.");
        }
        return target;
    }
}
