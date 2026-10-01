package com.be.trainingproduct.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TrainingProductExcelReaderTest {
    private final TrainingProductExcelReader reader = new TrainingProductExcelReader();

    @Test
    void readsProductRowsByHeaderNameAndSkipsBlankProductNames() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("다른 열");
            header.createCell(2).setCellValue("마이 카테고리 코드");
            header.createCell(4).setCellValue("상품명");
            var product = sheet.createRow(1);
            product.createCell(2).setCellValue("MY1");
            product.createCell(4).setCellValue("테스트 상품");
            sheet.createRow(2).createCell(2).setCellValue("MY2");
            workbook.write(output);

            var file = new MockMultipartFile("files", "products.xlsx", "application/octet-stream", output.toByteArray());
            reader.validateFiles(List.of(file));

            assertThat(reader.readRows(file)).singleElement().satisfies(row -> {
                assertThat(row.rowNumber()).isEqualTo(2);
                assertThat(row.productName()).isEqualTo("테스트 상품");
                assertThat(row.myCategoryCode()).isEqualTo("MY1");
            });
        }
    }
}
