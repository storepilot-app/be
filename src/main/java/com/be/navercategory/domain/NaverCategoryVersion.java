package com.be.navercategory.domain;

import java.nio.file.Path;
import java.time.Instant;

public record NaverCategoryVersion(
        long versionId,
        String sourceFilename,
        int rowCount,
        int categoryCount,
        Path uploadedFilePath,
        Path csvFilePath,
        Instant uploadedAt
) {
}
