package com.be.categorymatcher.dto;

import java.util.ArrayList;
import java.util.List;

public record ImageProductAnalysis(
        String productType,
        List<String> colors,
        List<String> forms,
        List<String> visibleText,
        List<String> uncertainties,
        boolean imageMatchesProductName,
        double confidence
) {
    public List<String> keywordTerms() {
        if (!imageMatchesProductName || productType == null || productType.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        terms.add(productType);
        if (colors != null) terms.addAll(colors);
        if (forms != null) terms.addAll(forms);
        return terms.stream().filter(term -> term != null && !term.isBlank()).distinct().limit(11).toList();
    }
}
