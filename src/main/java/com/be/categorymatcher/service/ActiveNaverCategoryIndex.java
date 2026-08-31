package com.be.categorymatcher.service;

import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.navercategory.domain.NaverCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record ActiveNaverCategoryIndex(
        Long versionId,
        Map<Long, NaverCategory> categoriesById,
        Map<String, NaverCategory> categoriesByCode,
        Map<String, NaverCategory> categoriesByFullPath
) {
    static ActiveNaverCategoryIndex from(Long versionId, List<NaverCategory> categories) {
        Map<Long, NaverCategory> categoriesById = new HashMap<>();
        Map<String, NaverCategory> categoriesByCode = new HashMap<>();
        Map<String, NaverCategory> categoriesByFullPath = new HashMap<>();

        for (NaverCategory category : categories) {
            if (category.getId() != null) {
                categoriesById.putIfAbsent(category.getId(), category);
            }
            if (category.getCategoryCode() != null && !category.getCategoryCode().isBlank()) {
                categoriesByCode.putIfAbsent(category.getCategoryCode(), category);
            }
            if (category.getFullPath() != null && !category.getFullPath().isBlank()) {
                categoriesByFullPath.putIfAbsent(category.getFullPath(), category);
            }
        }

        return new ActiveNaverCategoryIndex(
                versionId,
                Map.copyOf(categoriesById),
                Map.copyOf(categoriesByCode),
                Map.copyOf(categoriesByFullPath)
        );
    }

    Optional<NaverCategory> find(CategoryMatchPrediction prediction) {
        NaverCategory category = prediction.categoryId() == null
                ? null
                : categoriesById.get(prediction.categoryId());
        if (category == null && prediction.categoryCode() != null) {
            category = categoriesByCode.get(prediction.categoryCode());
        }
        if (category == null && prediction.fullPath() != null) {
            category = categoriesByFullPath.get(prediction.fullPath());
        }
        return Optional.ofNullable(category);
    }
}
