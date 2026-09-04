package com.be.trainingproductrequest.dto;

import com.be.trainingproductrequest.domain.TrainingProductRequest;
import com.be.trainingproductrequest.domain.TrainingProductRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "카테고리 학습 요청")
public record TrainingProductRequestResponse(
        Long id,
        Long userId,
        String userEmail,
        String originalFilename,
        long fileSize,
        int productCount,
        TrainingProductRequestStatus status,
        boolean fileAvailable,
        Instant createdAt
) {
    public static TrainingProductRequestResponse from(TrainingProductRequest request) {
        return new TrainingProductRequestResponse(
                request.getId(),
                request.getUserId(),
                request.getUserEmail(),
                request.getOriginalFilename(),
                request.getFileSize(),
                request.getProductCount(),
                request.getStatus(),
                request.getFileDeletedAt() == null,
                request.getCreatedAt()
        );
    }
}
