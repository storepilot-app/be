package com.be.trainingproduct.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.service.MyCategoryMappingUploadService;
import com.be.trainingproduct.client.TrainingProductAiClient;
import com.be.trainingproduct.dto.ProductIndexAppendAiResponse;
import com.be.trainingproduct.service.TrainingProductExcelReader.TrainingProductExcelRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class TrainingProductServiceTest {
    private TrainingProductAiClient aiClient;
    private MyCategoryMappingUploadService mappingUploadService;
    private TrainingProductExcelReader excelReader;
    private TrainingProductService service;

    @BeforeEach
    void setUp() {
        aiClient = mock(TrainingProductAiClient.class);
        mappingUploadService = mock(MyCategoryMappingUploadService.class);
        excelReader = mock(TrainingProductExcelReader.class);
        service = new TrainingProductService(aiClient, mappingUploadService, excelReader);
    }

    @Test
    void previewsMatchedMissingAndUnknownMappingsWithoutCallingAi() {
        var productFile = file("products.xlsx");
        var mappingFile = file("mappings.xlsx");
        when(mappingUploadService.readMappings(mappingFile, 1L)).thenReturn(List.of(
                MyCategoryMapping.create(1L, "001", "500", 10L, "500", "의류 > 셔츠"),
                MyCategoryMapping.create(1L, "002", "999", null, null, null)
        ));
        when(excelReader.readRows(productFile)).thenReturn(List.of(
                new TrainingProductExcelRow(2, "셔츠", "001"),
                new TrainingProductExcelRow(3, "가방", "003"),
                new TrainingProductExcelRow(4, "모자", ""),
                new TrainingProductExcelRow(5, "신발", "002")
        ));

        var result = service.previewMappings(1L, productFile, mappingFile);

        assertThat(result.products()).hasSize(4);
        assertThat(result.products().get(0).naverCategoryFullPath()).isEqualTo("의류 > 셔츠");
        assertThat(result.products().get(0).reason()).isNull();
        assertThat(result.products().get(1).reason()).contains("매핑 파일");
        assertThat(result.products().get(2).reason()).contains("코드가 없습니다");
        assertThat(result.products().get(3).reason()).contains("활성 네이버");
        verify(excelReader).validateFiles(List.of(productFile));
        verifyNoInteractions(aiClient);
    }

    @Test
    void appendsOnlyRowsWithResolvedMappings() {
        var firstFile = file("products-1.xlsx");
        var secondFile = file("products-2.xlsx");
        var files = List.of(firstFile, secondFile);
        var mappingFile = file("mappings.xlsx");
        var mapping = MyCategoryMapping.create(1L, "MY1", "500", 10L, "500", "카테고리");
        when(mappingUploadService.readResolvedMappings(mappingFile, 1L)).thenReturn(List.of(mapping));
        when(excelReader.readRows(files)).thenReturn(List.of(
                new TrainingProductExcelRow(2, "상품1", "MY1"),
                new TrainingProductExcelRow(3, "상품2", "UNKNOWN")
        ));
        when(aiClient.appendProducts(any())).thenReturn(new ProductIndexAppendAiResponse(1L, 100, 1, 0, "ok"));

        var result = service.appendProducts(1L, files, mappingFile);

        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.sourceRowCount()).isEqualTo(2);
        assertThat(result.validRowCount()).isEqualTo(1);
        assertThat(result.unmappedRowCount()).isEqualTo(1);
        assertThat(result.insertedProductCount()).isEqualTo(1);
        assertThat(result.indexedProductCount()).isEqualTo(100);
        verify(excelReader).validateFiles(files);
        verify(aiClient).appendProducts(any());
    }

    private MultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream", new byte[]{1});
    }
}
