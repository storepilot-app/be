package com.be.trainingproductrequest.dto;

import com.be.trainingproductrequest.domain.TrainingProductRequestStatus;

public record TrainingProductRequestStatusUpdateRequest(
        TrainingProductRequestStatus status
) {
}
