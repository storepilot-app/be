package com.be.categorymatcher.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
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

    public Optional<String> findMyCategoryCode(String productName, String userKey) {
        if (productName == null || productName.isBlank() || userKey == null || userKey.isBlank()) {
            return Optional.empty();
        }

        Optional<NaverCategoryVersion> activeVersion = naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc();
        if (activeVersion.isEmpty()) {
            return Optional.empty();
        }

        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(activeVersion.get().getId());
        Optional<NaverCategory> category = findByRule(productName, categories)
                .or(() -> findByAi(activeVersion.get().getId(), productName, categories));

        return category.flatMap(naverCategory -> findMapping(userKey.trim(), naverCategory))
                .map(MyCategoryMapping::getMyCategoryCode);
    }

    public void rebuildEmbeddings(Long versionId) {
        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(versionId);
        if (!categories.isEmpty()) {
            categoryMatcherAiClient.rebuild(versionId, categories);
        }
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
        return categoryMatcherAiClient.predict(versionId, List.of(new CategoryMatchProductRequest(1, productName)))
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
