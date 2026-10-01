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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductCategoryFeedbackServiceTest {
    @Test
    void storesFeedbackAndUpdatesIndexAndStats() {
        TrainingProductAiClient aiClient = mock(TrainingProductAiClient.class);
        MyCategoryMappingQueryService mappingQueryService = mock(MyCategoryMappingQueryService.class);
        ProductCategoryFeedbackRepository repository = mock(ProductCategoryFeedbackRepository.class);
        ProductCategoryStatService statService = mock(ProductCategoryStatService.class);
        ProductCategoryFeedbackService service = new ProductCategoryFeedbackService(
                aiClient,
                mappingQueryService,
                repository,
                statService
        );
        MyCategoryMapping mapping = MyCategoryMapping.create(
                1L,
                "MY1",
                "500",
                10L,
                "500",
                "생활/건강 > 의료용품"
        );
        when(mappingQueryService.getRequiredResolvedMapping(1L, "MY1")).thenReturn(mapping);
        when(repository.findFirstByUserIdAndNormalizedProductKeyOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiClient.addProductFeedback(any())).thenReturn(new ProductFeedbackAiResponse(1L, 100, "ok"));

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
}
