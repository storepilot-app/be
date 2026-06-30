package com.be.categorymatcher.dto;

public record CategoryMatchCandidate(
        Long categoryId,
        String categoryCode,
        String fullPath,
        double score
) {
}
