package com.be.trainingproduct.dto;

public record ProductIndexAppendAiResponse(Long userId, int indexedProductCount,
        int insertedProductCount, int updatedProductCount, String message) {
}
