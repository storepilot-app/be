package com.be.productexceljob.service;

import java.nio.file.Path;

record ProductExcelProcessingRequest(
        Path filePath,
        String originalFilename,
        String productNameColumn,
        String categoryColumn,
        Integer keywordCount,
        Long userId,
        boolean includeSelectionDetails
) {
}
