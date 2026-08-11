package com.be.watermark.repository;

import com.be.watermark.domain.UserWatermark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWatermarkRepository extends JpaRepository<UserWatermark, Long> {
    Optional<UserWatermark> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
