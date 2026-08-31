package com.be.categorymatcher.service;

import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryMatcherService {
    private final CategoryPredictionBatchProcessor categoryPredictionBatchProcessor;
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;
    private final MyCategoryMappingRepository myCategoryMappingRepository;

    public Map<Integer, MyCategoryMatchResult> findCategoryMatches(
            List<CategoryMatchProductRequest> products,
            Long userId,
            IntConsumer batchProgress
    ) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        IntConsumer progress = batchProgress == null
                ? ignored -> {}
                : batchProgress;
        Map<Integer, MyCategoryMatchResult> defaultResults = createDefaultResults(products);

        if (userId == null) {
            progress.accept(products.size());
            return defaultResults;
        }

        Optional<ActiveNaverCategoryIndex> activeCategoryIndex = loadActiveNaverCategoryIndex();
        if (activeCategoryIndex.isEmpty()) {
            progress.accept(products.size());
            return defaultResults;
        }

        // AI 카테고리 예측
        ActiveNaverCategoryIndex categoryIndex = activeCategoryIndex.get();
        Map<Integer, CategoryMatchPrediction> predictions = categoryPredictionBatchProcessor.predict(
                categoryIndex.versionId(),
                products,
                progress
        );
        Map<Integer, NaverCategory> matchedCategoriesByRowId = resolveMatchedCategoriesByRowId(
                predictions,
                categoryIndex
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

    //모든 상품 행의 기본 결과를 매칭 없음으로 만들어두는 메서드
    private Map<Integer, MyCategoryMatchResult> createDefaultResults(List<CategoryMatchProductRequest> products) {
        return products.stream()
                .collect(Collectors.toMap(
                        CategoryMatchProductRequest::rowId,
                        product -> MyCategoryMatchResult.noCategoryMatch(),
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private Optional<ActiveNaverCategoryIndex> loadActiveNaverCategoryIndex() {
        return naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc()
                .map(version -> ActiveNaverCategoryIndex.from(
                        version.getId(),
                        naverCategoryRepository.findByVersionId(version.getId())
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
            ActiveNaverCategoryIndex categoryIndex
    ) {
        Map<Integer, NaverCategory> matchedCategories = new HashMap<>();
        for (CategoryMatchPrediction prediction : predictions.values()) {
            categoryIndex.find(prediction)
                    .ifPresent(category -> matchedCategories.put(prediction.rowId(), category));
        }
        return matchedCategories;
    }

    private Map<String, MyCategoryMapping> loadMappingsByNaverCategoryCode(
            Long userId,
            Collection<NaverCategory> categories
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

    private record ActiveNaverCategoryIndex(
            Long versionId,
            Map<Long, NaverCategory> categoriesById,
            Map<String, NaverCategory> categoriesByCode,
            Map<String, NaverCategory> categoriesByFullPath
    ) {
        private static ActiveNaverCategoryIndex from(Long versionId, List<NaverCategory> categories) {
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

        private Optional<NaverCategory> find(CategoryMatchPrediction prediction) {
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

}
