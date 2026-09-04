package com.be.trainingproductrequest.dto;

import com.be.trainingproductrequest.domain.TrainingProductRequest;
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
                request.getCreatedAt()
        );
    }
}
