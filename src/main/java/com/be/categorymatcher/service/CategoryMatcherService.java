package com.be.categorymatcher.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchPredictResponse;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryMatcherService {
    private final CategoryMatcherAiClient categoryMatcherAiClient;
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;
    private final MyCategoryMappingRepository myCategoryMappingRepository;

    public Map<Integer, MyCategoryMatchResult> findCategoryMatches(
            List<CategoryMatchProductRequest> products,
            Long userId
    ) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, MyCategoryMatchResult> defaultResults = createDefaultResults(products);
        if (userId == null) {
            return defaultResults;
        }

        Optional<ActiveNaverCategories> activeCategories = loadActiveNaverCategories();
        if (activeCategories.isEmpty()) {
            return defaultResults;
        }

        // AI 카테고리 예측
        Map<Integer, CategoryMatchPrediction> predictions = predictBatchByAi(
                activeCategories.get().versionId(),
                products
        );

        List<NaverCategory> categories = activeCategories.get().categories(); //현재 활성 네이버 카테고리 목록
        Map<Long, NaverCategory> categoriesById = mapCategoriesById(categories); //id 기준 빠른 조회 Map
        
        Map<Integer, NaverCategory> matchedCategoriesByRowId = resolveMatchedCategoriesByRowId(
                predictions,
                categories,
                categoriesById
        );
        Map<String, MyCategoryMapping> mappingsByNaverCategoryCode = loadMappingsByNaverCategoryCode(
                userId,
                matchedCategoriesByRowId.values()
        );

        Map<Integer, MyCategoryMatchResult> results = new HashMap<>(defaultResults);
        for (CategoryMatchProductRequest product : products) {
            CategoryMatchPrediction prediction = predictions.get(product.rowId());
            if (prediction == null) {
                continue;
            }

            NaverCategory matchedCategory = matchedCategoriesByRowId.get(product.rowId());
            MyCategoryMatchResult result = toMatchResult(
                    prediction,
                    matchedCategory,
                    mappingsByNaverCategoryCode
            );
            results.put(product.rowId(), result);
        }

        return results;
    }

    private Map<Integer, CategoryMatchPrediction> predictBatchByAi(
            Long versionId,
            List<CategoryMatchProductRequest> products
    ) {
        Optional<CategoryMatchPredictResponse> response = categoryMatcherAiClient.predict(versionId, products);
        if (response.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CategoryMatchPrediction> predictions = response.get().results();
        if (predictions == null || predictions.isEmpty()) {
            return Collections.emptyMap();
        }

        return toPredictionMap(predictions);
    }

    private Map<Integer, MyCategoryMatchResult> createDefaultResults(List<CategoryMatchProductRequest> products) {
        return products.stream()
                .collect(Collectors.toMap(
                        CategoryMatchProductRequest::rowId,
                        product -> MyCategoryMatchResult.noCategoryMatch(),
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private Optional<ActiveNaverCategories> loadActiveNaverCategories() {
        return naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc()
                .map(version -> new ActiveNaverCategories(
                        version.getId(),
                        naverCategoryRepository.findByVersionId(version.getId())
                ));
    }

    private Map<Integer, CategoryMatchPrediction> toPredictionMap(List<CategoryMatchPrediction> predictions) {
        return predictions.stream()
                .collect(Collectors.toMap(
                        CategoryMatchPrediction::rowId,
                        Function.identity(),
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private Map<Long, NaverCategory> mapCategoriesById(List<NaverCategory> categories) {
        return categories.stream()
                .collect(Collectors.toMap(
                        NaverCategory::getId,
                        category -> category,
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private List<CategoryMatchCandidate> topCandidates(CategoryMatchPrediction prediction) {
        if (prediction.candidates() == null || prediction.candidates().isEmpty()) {
            return List.of();
        }
        return prediction.candidates().stream()
                .filter(candidate -> candidate.fullPath() != null && !candidate.fullPath().isBlank())
                .limit(10)
                .toList();
    }

    private List<CategoryMatchSimilarProduct> similarProducts(CategoryMatchPrediction prediction) {
        return prediction.similarProducts() == null ? List.of() : prediction.similarProducts();
    }

    private MyCategoryMatchResult toMatchResult(
            CategoryMatchPrediction prediction,
            NaverCategory matchedCategory,
            Map<String, MyCategoryMapping> mappingsByNaverCategoryCode
    ) {
        List<CategoryMatchCandidate> topNaverCategoryCandidates = topCandidates(prediction);
        String llmSelectedCategory = llmSelectedCategory(prediction);
        String llmStatus = prediction.llmStatus();
        String llmStatusDetail = prediction.llmStatusDetail();
        List<CategoryMatchSimilarProduct> similarProducts = similarProducts(prediction);

        if (matchedCategory == null) {
            return MyCategoryMatchResult
                    .noCategoryMatch(topNaverCategoryCandidates, llmSelectedCategory, llmStatus, llmStatusDetail)
                    .withSimilarProducts(similarProducts);
        }

        MyCategoryMapping mapping = mappingsByNaverCategoryCode.get(matchedCategory.getCategoryCode());
        MyCategoryMatchResult result = mapping == null
                ? MyCategoryMatchResult.noMyCategoryMapping(
                        matchedCategory.getFullPath(),
                        topNaverCategoryCandidates,
                        llmSelectedCategory,
                        llmStatus,
                        llmStatusDetail
                )
                : MyCategoryMatchResult.matched(
                        mapping.getMyCategoryCode(),
                        matchedCategory.getFullPath(),
                        topNaverCategoryCandidates,
                        llmSelectedCategory,
                        llmStatus,
                        llmStatusDetail
                );
        return result.withSimilarProducts(similarProducts);
    }

    private String llmSelectedCategory(CategoryMatchPrediction prediction) {
        return Boolean.TRUE.equals(prediction.llmUsed()) ? prediction.llmSelectedCategory() : null;
    }

    private Map<Integer, NaverCategory> resolveMatchedCategoriesByRowId(
            Map<Integer, CategoryMatchPrediction> predictions,
            List<NaverCategory> categories,
            Map<Long, NaverCategory> categoriesById
    ) {
        Map<Integer, NaverCategory> matchedCategories = new HashMap<>();
        for (CategoryMatchPrediction prediction : predictions.values()) {
            resolveMatchedCategory(prediction, categories, categoriesById)
                    .ifPresent(category -> matchedCategories.put(prediction.rowId(), category));
        }
        return matchedCategories;
    }

    private Optional<NaverCategory> resolveMatchedCategory(
            CategoryMatchPrediction prediction,
            List<NaverCategory> categories,
            Map<Long, NaverCategory> categoriesById
    ) {
        if (prediction.categoryId() != null) {
            NaverCategory category = categoriesById.get(prediction.categoryId());
            if (category != null) {
                return Optional.of(category);
            }
        }
        return findCategoryFromPrediction(categories, prediction);
    }

    private Map<String, MyCategoryMapping> loadMappingsByNaverCategoryCode(
            Long userId,
            Iterable<NaverCategory> categories
    ) {
        Set<String> naverCategoryCodes = new HashSet<>();
        for (NaverCategory category : categories) {
            if (category.getCategoryCode() != null && !category.getCategoryCode().isBlank()) {
                naverCategoryCodes.add(category.getCategoryCode());
            }
        }
        if (naverCategoryCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        return myCategoryMappingRepository.findByUserIdAndNaverCategoryCodeIn(userId, naverCategoryCodes)
                .stream()
                .filter(mapping -> mapping.getNaverCategoryCode() != null && !mapping.getNaverCategoryCode().isBlank())
                .collect(Collectors.toMap(
                        MyCategoryMapping::getNaverCategoryCode,
                        Function.identity(),
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private Optional<NaverCategory> findCategoryFromPrediction(List<NaverCategory> categories, CategoryMatchPrediction prediction) {
        return categories.stream()
                .filter(category -> matchesPrediction(category, prediction))
                .findFirst();
    }

    private boolean matchesPrediction(NaverCategory category, CategoryMatchPrediction prediction) {
        return matchesCategoryId(category, prediction)
                || matchesCategoryCode(category, prediction)
                || matchesFullPath(category, prediction);
    }

    private boolean matchesCategoryId(NaverCategory category, CategoryMatchPrediction prediction) {
        return prediction.categoryId() != null
                && prediction.categoryId().equals(category.getId());
    }

    private boolean matchesCategoryCode(NaverCategory category, CategoryMatchPrediction prediction) {
        return prediction.categoryCode() != null
                && prediction.categoryCode().equals(category.getCategoryCode());
    }

    private boolean matchesFullPath(NaverCategory category, CategoryMatchPrediction prediction) {
        return prediction.fullPath() != null
                && prediction.fullPath().equals(category.getFullPath());
    }

    private record ActiveNaverCategories(Long versionId, List<NaverCategory> categories) {
    }

}
