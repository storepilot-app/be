package com.be.trainingproduct.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingQueryService;
import com.be.mycategory.service.MyCategoryMappingUploadService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.dto.ProductIndexAppendAiResponse;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TrainingProductAppendTest {
    @Test
    void appendUsesIndexCountsWithoutFeedbackOrStatDatabaseCalls() throws Exception {
        var ai = mock(TrainingProductAiClient.class);
        var mappings = mock(MyCategoryMappingUploadService.class);
        var feedback = mock(ProductCategoryFeedbackRepository.class);
        var stats = mock(ProductCategoryStatService.class);
        var service = new TrainingProductService(ai, mock(MyCategoryMappingQueryService.class), mappings, feedback, stats);
        var mapping = mock(MyCategoryMapping.class);
        when(mapping.getMyCategoryCode()).thenReturn("MY1");
        when(mapping.getNaverCategoryId()).thenReturn(1L);
        when(mapping.getNaverCategoryCode()).thenReturn("A");
        when(mapping.getNaverCategoryFullPath()).thenReturn("카테고리");
        var mappingFile = new MockMultipartFile("myCategoryFile", "mapping.xlsx", "application/octet-stream", new byte[]{1});
        when(mappings.readResolvedMappings(mappingFile, 1L)).thenReturn(List.of(mapping));
        when(ai.appendProducts(any())).thenReturn(new ProductIndexAppendAiResponse(1L, 100, 0, 1, "ok"));
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("상품명");
            header.createCell(1).setCellValue("마이카테");
            for (int i = 1; i <= 2; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("같은 상품");
                row.createCell(1).setCellValue("MY1");
            }
            workbook.write(output);
            var file = new MockMultipartFile("files", "products.xlsx", "application/octet-stream", output.toByteArray());
            var result = service.appendProducts(1L, List.of(file), mappingFile);
            assertEquals(2, result.appendedProductCount());
            assertEquals(0, result.insertedProductCount());
            assertEquals(1, result.updatedProductCount());
            assertEquals(100, result.indexedProductCount());
            verifyNoInteractions(feedback, stats);
        }
    }
}
