package com.be.trainingproductrequest.dto;

import com.be.trainingproductrequest.domain.TrainingProductRequest;
import java.util.List;

public record TrainingProductRequestListResponse(
        int requestCount,
        List<TrainingProductRequestResponse> requests
) {
    public static TrainingProductRequestListResponse from(List<TrainingProductRequest> requests) {
        List<TrainingProductRequestResponse> responses = requests.stream()
                .map(TrainingProductRequestResponse::from)
                .toList();
        return new TrainingProductRequestListResponse(responses.size(), responses);
    }
}
