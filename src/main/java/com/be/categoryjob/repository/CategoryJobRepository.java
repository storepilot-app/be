package com.be.categoryjob.repository;

import com.be.categoryjob.domain.CategoryJob;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryJobRepository {
    private final Map<Long, CategoryJob> jobs = new ConcurrentHashMap<>();

    public CategoryJob save(CategoryJob job) {
        jobs.put(job.getJobId(), job);
        return job;
    }

    public Optional<CategoryJob> findById(long jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
