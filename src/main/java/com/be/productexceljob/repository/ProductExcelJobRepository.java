package com.be.productexceljob.repository;

import com.be.productexceljob.domain.ProductExcelJob;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class ProductExcelJobRepository {
    private final Map<Long, ProductExcelJob> jobs = new ConcurrentHashMap<>();

    public ProductExcelJob save(ProductExcelJob job) {
        jobs.put(job.getJobId(), job);
        return job;
    }

    public Optional<ProductExcelJob> findById(long jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
