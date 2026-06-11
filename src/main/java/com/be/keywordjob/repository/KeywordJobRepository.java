package com.be.keywordjob.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.be.keywordjob.domain.KeywordJob;
import org.springframework.stereotype.Repository;

@Repository
public class KeywordJobRepository {
    private final Map<Long, KeywordJob> jobs = new ConcurrentHashMap<>();

    public KeywordJob save(KeywordJob job) {
        jobs.put(job.getJobId(), job);
        return job;
    }

    public Optional<KeywordJob> findById(long jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
