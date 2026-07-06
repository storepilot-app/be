package com.be.categorymatcher.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchCandidate;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.CategoryMatchSimilarProduct;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public Map<Integer, MyCategoryMatchResult> findMyCategoryCodes(
            List<CategoryMatchProductRequest> products,
            String userKey
    ) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, MyCategoryMatchResult> defaultResults = products.stream()
                .collect(Collectors.toMap(
                        CategoryMatchProductRequest::rowId,
                        product -> MyCategoryMatchResult.noCategoryMatch(),
                        (first, second) -> first,
                        HashMap::new
                ));

        if (userKey == null || userKey.isBlank()) {
            return defaultResults;
        }

        Optional<NaverCategoryVersion> activeVersion = naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc();
        if (activeVersion.isEmpty()) {
            return defaultResults;
        }

        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(activeVersion.get().getId());
        Map<Integer, CategoryMatchPrediction> predictions = predictBatchByAi(
                activeVersion.get().getId(),
                products,
                categories
        );
        Map<Long, NaverCategory> categoriesById = categories.stream()
                .collect(Collectors.toMap(NaverCategory::getId, Function.identity(), (first, second) -> first));
        Map<Integer, MyCategoryMatchResult> results = new HashMap<>(defaultResults);

        for (CategoryMatchProductRequest product : products) {
            CategoryMatchPrediction prediction = predictions.get(product.rowId());
            if (prediction == null) {
                continue;
            }

            List<CategoryMatchCandidate> topNaverCategoryCandidates = topCandidates(prediction);
            String llmSelectedCategory = Boolean.TRUE.equals(prediction.llmUsed()) ? prediction.llmSelectedCategory() : null;
            String llmStatus = prediction.llmStatus();
            String llmStatusDetail = prediction.llmStatusDetail();
            List<CategoryMatchSimilarProduct> similarProducts = similarProducts(prediction);
            NaverCategory category = prediction.categoryId() == null ? null : categoriesById.get(prediction.categoryId());
            if (category == null) {
                category = findCategoryFromPrediction(categories, prediction).orElse(null);
            }

            if (category == null) {
                results.put(product.rowId(), MyCategoryMatchResult
                        .noCategoryMatch(topNaverCategoryCandidates, llmSelectedCategory, llmStatus, llmStatusDetail)
                        .withSimilarProducts(similarProducts));
                continue;
            }

            NaverCategory matchedCategory = category;
            MyCategoryMatchResult result = findMapping(userKey.trim(), matchedCategory)
                    .map(mapping -> MyCategoryMatchResult.matched(
                            mapping.getMyCategoryCode(),
                            matchedCategory.getFullPath(),
                            topNaverCategoryCandidates,
                            llmSelectedCategory,
                            llmStatus,
                            llmStatusDetail
                    ))
                    .orElseGet(() -> MyCategoryMatchResult.noMyCategoryMapping(
                            matchedCategory.getFullPath(),
                            topNaverCategoryCandidates,
                            llmSelectedCategory,
                            llmStatus,
                            llmStatusDetail
                    ));
            results.put(product.rowId(), result.withSimilarProducts(similarProducts));
        }

        return results;
    }

    public void rebuildEmbeddings(Long versionId) {
        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(versionId);
        if (!categories.isEmpty()) {
            categoryMatcherAiClient.rebuild(versionId, categories);
        }
    }

    public Optional<Long> rebuildActiveEmbeddings() {
        Optional<NaverCategoryVersion> activeVersion = naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc();
        activeVersion.ifPresent(version -> rebuildEmbeddings(version.getId()));
        return activeVersion.map(NaverCategoryVersion::getId);
    }

    private Map<Integer, CategoryMatchPrediction> predictBatchByAi(
            Long versionId,
            List<CategoryMatchProductRequest> products,
            List<NaverCategory> categories,
            boolean rebuildOnMissingCache
    ) {
        Optional<List<CategoryMatchPrediction>> firstResult = categoryMatcherAiClient.predict(versionId, products)
                .map(response -> response.results() == null ? List.<CategoryMatchPrediction>of() : response.results());
        if (firstResult.isPresent() && !firstResult.get().isEmpty()) {
            return toPredictionMap(firstResult.get());
        }

        if (rebuildOnMissingCache) {
            categoryMatcherAiClient.rebuild(versionId, categories);
            return categoryMatcherAiClient.predict(versionId, products)
                    .map(response -> response.results() == null ? List.<CategoryMatchPrediction>of() : response.results())
                    .map(this::toPredictionMap)
                    .orElseGet(Collections::emptyMap);
        }

        return Collections.emptyMap();
    }

    private Map<Integer, CategoryMatchPrediction> predictBatchByAi(
            Long versionId,
            List<CategoryMatchProductRequest> products,
            List<NaverCategory> categories
    ) {
        return predictBatchByAi(versionId, products, categories, true);
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

    private Optional<NaverCategory> findCategoryFromPrediction(List<NaverCategory> categories, CategoryMatchPrediction prediction) {
        return categories.stream()
                .filter(category -> prediction.categoryId() != null && prediction.categoryId().equals(category.getId())
                        || prediction.categoryCode() != null && prediction.categoryCode().equals(category.getCategoryCode())
                        || prediction.fullPath() != null && prediction.fullPath().equals(category.getFullPath()))
                .findFirst();
    }

    private Optional<MyCategoryMapping> findMapping(String userKey, NaverCategory naverCategory) {
        return myCategoryMappingRepository.findFirstByUserKeyAndNaverCategoryCode(
                userKey,
                naverCategory.getCategoryCode()
        );
    }

}
