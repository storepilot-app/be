package com.be.trainingproductrequest.dto;

public record TrainingProductRequestFile(
        String filename,
        byte[] content
) {
}
