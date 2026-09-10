package com.be.trainingproduct.dto;

import java.util.List;

public record ProductMappingPreviewResponse(List<Item> products) {
    public record Item(int rowNumber, String productName, String myCategoryCode,
                       String naverCategoryCode, String naverCategoryFullPath, String reason) {
    }
}
