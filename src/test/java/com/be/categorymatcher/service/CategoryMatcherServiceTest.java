package com.be.categorymatcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchPredictResponse;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryMatcherServiceTest {
    private CategoryMatcherAiClient categoryMatcherAiClient;
    private NaverCategoryRepository naverCategoryRepository;
    private NaverCategoryVersionRepository naverCategoryVersionRepository;
    private MyCategoryMappingRepository myCategoryMappingRepository;
    private CategoryPredictionBatchProcessor categoryPredictionBatchProcessor;
    private CategoryMatcherService categoryMatcherService;

    @BeforeEach
    void setUp() {
        categoryMatcherAiClient = mock(CategoryMatcherAiClient.class);
        naverCategoryRepository = mock(NaverCategoryRepository.class);
        naverCategoryVersionRepository = mock(NaverCategoryVersionRepository.class);
        myCategoryMappingRepository = mock(MyCategoryMappingRepository.class);
        categoryPredictionBatchProcessor = new CategoryPredictionBatchProcessor(categoryMatcherAiClient);
        ReflectionTestUtils.setField(categoryPredictionBatchProcessor, "batchSize", 2);
        categoryMatcherService = new CategoryMatcherService(
                categoryPredictionBatchProcessor,
                naverCategoryRepository,
                naverCategoryVersionRepository,
                myCategoryMappingRepository
        );
    }

    @Test
    void loadsActiveCategoriesOnceAndReusesThemForEveryAiBatch() {
        NaverCategoryVersion version = NaverCategoryVersion.createActive(
                "categories.xlsx",
                1,
                1,
                "categories.xlsx",
                "categories.csv",
                Instant.now()
        );
        ReflectionTestUtils.setField(version, "id", 7L);
        NaverCategory category = NaverCategory.create("500", "디지털", "", "", "");

        when(naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc())
                .thenReturn(Optional.of(version));
        when(naverCategoryRepository.findByVersionId(7L)).thenReturn(List.of(category));
        when(categoryMatcherAiClient.predict(eq(7L), anyList())).thenAnswer(invocation -> {
            List<CategoryMatchProductRequest> batch = invocation.getArgument(1);
            List<CategoryMatchPrediction> predictions = batch.stream()
                    .map(product -> prediction(product.rowId(), category.getCategoryCode(), category.getFullPath()))
                    .toList();
            return Optional.of(new CategoryMatchPredictResponse(predictions));
        });
        when(myCategoryMappingRepository.findByUserIdAndNaverCategoryCodeIn(eq(9L), anyCollection()))
                .thenReturn(List.of());
        List<Integer> processedCounts = new ArrayList<>();

        Map<Integer, MyCategoryMatchResult> results = categoryMatcherService.findCategoryMatches(
                List.of(
                        new CategoryMatchProductRequest(1, "상품 1"),
                        new CategoryMatchProductRequest(2, "상품 2"),
                        new CategoryMatchProductRequest(3, "상품 3")
                ),
                9L,
                processedCounts::add
        );

        assertEquals(List.of(2, 3), processedCounts);
        assertEquals("디지털", results.get(1).naverCategory());
        assertEquals("디지털", results.get(2).naverCategory());
        assertEquals("디지털", results.get(3).naverCategory());
        verify(naverCategoryVersionRepository, times(1)).findFirstByActiveTrueOrderByUploadedAtDesc();
        verify(naverCategoryRepository, times(1)).findByVersionId(7L);
        verify(categoryMatcherAiClient, times(2)).predict(eq(7L), anyList());
    }

    private CategoryMatchPrediction prediction(int rowId, String categoryCode, String fullPath) {
        return new CategoryMatchPrediction(
                rowId,
                null,
                categoryCode,
                fullPath,
                0.9,
                List.of(),
                false,
                null,
                "AUTO_SELECTED",
                null,
                List.of()
        );
    }
}
