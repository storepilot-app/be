package com.be.trainingproductrequest.repository;

import com.be.trainingproductrequest.domain.TrainingProductRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingProductRequestRepository extends JpaRepository<TrainingProductRequest, Long> {
    List<TrainingProductRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TrainingProductRequest> findAllByOrderByCreatedAtDesc();
}
