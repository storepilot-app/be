package com.be.trainingproduct.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.domain.ProductCategoryFeedback;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductFeedbackAiResponse;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductCategoryFeedbackServiceTest {
    private TrainingProductAiClient aiClient;
    private MyCategoryMappingQueryService mappingQueryService;
    private ProductCategoryFeedbackRepository repository;
    private ProductCategoryStatService statService;
    private ProductCategoryFeedbackService service;
    private MyCategoryMapping mapping;

    @BeforeEach
    void setUp() {
        aiClient = mock(TrainingProductAiClient.class);
        mappingQueryService = mock(MyCategoryMappingQueryService.class);
        repository = mock(ProductCategoryFeedbackRepository.class);
        statService = mock(ProductCategoryStatService.class);
        service = new ProductCategoryFeedbackService(aiClient, mappingQueryService, repository, statService);
        mapping = MyCategoryMapping.create(
                1L,
                "MY1",
                "500",
                10L,
                "500",
                "생활/건강 > 의료용품"
        );
        when(mappingQueryService.getRequiredResolvedMapping(1L, "MY1")).thenReturn(mapping);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiClient.addProductFeedback(any())).thenReturn(new ProductFeedbackAiResponse(1L, 100, "ok"));
    }

    @Test
    void storesFeedbackAndUpdatesIndexAndStats() {
        when(repository.findFirstByUserIdAndNormalizedProductKeyOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        var response = service.addFeedback(
                1L,
                new ProductCategoryFeedbackRequest(" 테스트 상품 ", "MY1")
        );

        assertThat(response.indexedProductCount()).isEqualTo(100);
        assertThat(response.naverCategory()).isEqualTo("생활/건강 > 의료용품");
        verify(repository).save(any(ProductCategoryFeedback.class));
        verify(aiClient).addProductFeedback(any());
        verify(statService).increaseStat(1L, mapping);
    }

    @Test
    void movesStatsWhenProductAlreadyHasFeedback() {
        var previousFeedback = ProductCategoryFeedback.create(
                1L,
                "테스트 상품",
                "테스트상품",
                "previous-key",
                "OLD",
                20L,
                "400",
                "기존 카테고리",
                Instant.now()
        );
        when(repository.findFirstByUserIdAndNormalizedProductKeyOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(previousFeedback));

        service.addFeedback(1L, new ProductCategoryFeedbackRequest("테스트 상품", "MY1"));

        verify(statService).moveStat(1L, "400", mapping);
    }
}
