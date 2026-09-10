package com.be.trainingproduct.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.be.auth.domain.UserRole;
import com.be.auth.security.LoginUser;
import com.be.global.exception.BusinessException;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingUploadService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.controller.TrainingProductController;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductMappingPreviewTest {
    @Test
    void showsMatchedMissingAndUnknownMappingsWithoutWritingOrCallingAi() throws Exception {
        var mappings = mock(MyCategoryMappingUploadService.class);
        var ai = mock(TrainingProductAiClient.class);
        var feedbacks = mock(ProductCategoryFeedbackRepository.class);
        var stats = mock(ProductCategoryStatService.class);
        var service = new TrainingProductService(ai, null, mappings, feedbacks, stats);
        var mappingFile = new MockMultipartFile("myCategoryFile", new byte[]{1});
        when(mappings.readMappings(mappingFile, 1L)).thenReturn(List.of(
                MyCategoryMapping.create(1L, "001", "500", 10L, "500", "의류 > 셔츠"),
                MyCategoryMapping.create(1L, "002", "999", null, null, null)));
        var result = service.previewMappings(1L, excel("마이카테", "상품명",
                new String[][]{{"001", "셔츠"}, {"003", "가방"}, {"", "모자"}, {"002", "신발"}, {"001", ""}}), mappingFile);
        assertThat(result.products()).hasSize(4);
        assertThat(result.products().get(0).naverCategoryFullPath()).isEqualTo("의류 > 셔츠");
        assertThat(result.products().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.products().get(0).reason()).isNull();
        assertThat(result.products().get(1).reason()).contains("매핑 파일");
        assertThat(result.products().get(2).reason()).contains("코드가 없습니다");
        assertThat(result.products().get(3).naverCategoryCode()).isEqualTo("999");
        assertThat(result.products().get(3).reason()).contains("활성 네이버");
        verifyNoInteractions(ai, feedbacks, stats);
        verify(mappings).readMappings(mappingFile, 1L);
        verifyNoMoreInteractions(mappings);
    }

    @Test
    void rejectsMissingProductHeader() throws Exception {
        var mappings = mock(MyCategoryMappingUploadService.class);
        var service = new TrainingProductService(null, null, mappings, null, null);
        var file = excel("마이카테", "잘못된헤더", new String[][]{});
        assertThatThrownBy(() -> service.previewMappings(1L, file, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("상품명");
    }

    @Test
    void rejectsNonAdminBeforeReadingFiles() {
        var service = mock(TrainingProductService.class);
        var controller = new TrainingProductController(service);
        assertThatThrownBy(() -> controller.previewMappings(new LoginUser(1L, "user@example.com", UserRole.USER), null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.previewMappings(null, null, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(service);
    }

    private MockMultipartFile excel(String first, String second, String[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(first);
            header.createCell(1).setCellValue(second);
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "products.xlsx", "application/octet-stream", output.toByteArray());
        }
    }
}
