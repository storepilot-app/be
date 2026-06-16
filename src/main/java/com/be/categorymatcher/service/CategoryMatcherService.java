package com.be.categorymatcher.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryMatcherService {
    private final CategoryMatcherAiClient categoryMatcherAiClient;
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;
    private final MyCategoryMappingRepository myCategoryMappingRepository;

    public MyCategoryMatchResult findMyCategoryCode(String productName, String userKey) {
        if (productName == null || productName.isBlank() || userKey == null || userKey.isBlank()) {
            return MyCategoryMatchResult.noCategoryMatch();
        }

        Optional<NaverCategoryVersion> activeVersion = naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc();
        if (activeVersion.isEmpty()) {
            return MyCategoryMatchResult.noCategoryMatch();
        }

        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(activeVersion.get().getId());
        Optional<NaverCategory> category = findByRule(productName, categories)
                .or(() -> findByAi(activeVersion.get().getId(), productName, categories));

        if (category.isEmpty()) {
            return MyCategoryMatchResult.noCategoryMatch();
        }

        return findMapping(userKey.trim(), category.get())
                .map(mapping -> MyCategoryMatchResult.matched(mapping.getMyCategoryCode()))
                .orElseGet(MyCategoryMatchResult::noMyCategoryMapping);
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

    private Optional<NaverCategory> findByRule(String productName, List<NaverCategory> categories) {
        String normalizedProductName = normalize(productName);
        return categories.stream()
                .filter(category -> !bestRuleKeyword(category).isBlank())
                .filter(category -> normalizedProductName.contains(normalize(bestRuleKeyword(category))))
                .max(Comparator.comparingInt(category -> normalize(bestRuleKeyword(category)).length()));
    }

    private String bestRuleKeyword(NaverCategory category) {
        if (category.getLevel4() != null && !category.getLevel4().isBlank()) {
            return category.getLevel4();
        }
        if (category.getLevel3() != null && !category.getLevel3().isBlank()) {
            return category.getLevel3();
        }
        if (category.getLevel2() != null && !category.getLevel2().isBlank()) {
            return category.getLevel2();
        }
        return category.getLevel1();
    }

    private Optional<NaverCategory> findByAi(Long versionId, String productName, List<NaverCategory> categories) {
        List<CategoryMatchProductRequest> products = List.of(new CategoryMatchProductRequest(1, productName));
        Optional<NaverCategory> firstResult = predictByAi(versionId, products, categories);
        if (firstResult.isPresent()) {
            return firstResult;
        }

        categoryMatcherAiClient.rebuild(versionId, categories);
        return predictByAi(versionId, products, categories);
    }

    private Optional<NaverCategory> predictByAi(
            Long versionId,
            List<CategoryMatchProductRequest> products,
            List<NaverCategory> categories
    ) {
        return categoryMatcherAiClient.predict(versionId, products)
                .flatMap(response -> response.results().stream().findFirst())
                .flatMap(prediction -> findCategoryFromPrediction(categories, prediction));
    }

    private Optional<NaverCategory> findCategoryFromPrediction(List<NaverCategory> categories, CategoryMatchPrediction prediction) {
        return categories.stream()
                .filter(category -> prediction.categoryId() != null && prediction.categoryId().equals(category.getId())
                        || prediction.categoryCode() != null && prediction.categoryCode().equals(category.getCategoryCode())
                        || prediction.fullPath() != null && prediction.fullPath().equals(category.getFullPath()))
                .findFirst();
    }

    private Optional<MyCategoryMapping> findMapping(String userKey, NaverCategory naverCategory) {
        return myCategoryMappingRepository.findFirstByUserKeyAndNaverCategoryCode(userKey, naverCategory.getCategoryCode())
                .or(() -> myCategoryMappingRepository.findFirstByUserKeyAndNaverCategoryFullPath(userKey, naverCategory.getFullPath()));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_/(),\\[\\]>-]+", "");
    }
}
