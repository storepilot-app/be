package com.be.trainingproduct.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.be.auth.domain.UserRole;
import com.be.auth.security.LoginUser;
import com.be.global.exception.BusinessException;
import com.be.trainingproduct.service.ProductCategoryFeedbackService;
import com.be.trainingproduct.service.TrainingProductService;
import org.junit.jupiter.api.Test;

class TrainingProductControllerTest {
    @Test
    void rejectsMappingPreviewBeforeCallingServiceWhenUserIsNotAdmin() {
        var trainingProductService = mock(TrainingProductService.class);
        var feedbackService = mock(ProductCategoryFeedbackService.class);
        var controller = new TrainingProductController(trainingProductService, feedbackService);

        assertThatThrownBy(() -> controller.previewMappings(
                new LoginUser(1L, "user@example.com", UserRole.USER),
                null,
                null
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.previewMappings(null, null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(trainingProductService, feedbackService);
    }
}
