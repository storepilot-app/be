package com.be.trainingproduct.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.be.global.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TrainingProductExcelReaderTest {
    private final TrainingProductExcelReader reader = new TrainingProductExcelReader();

    @Test
    void readsProductRowsByHeaderNameAndSkipsBlankProductNames() throws Exception {
        var file = excel("마이 카테고리 코드", "상품명", new String[][]{
                {"MY1", "테스트 상품"},
                {"MY2", ""}
        });

        assertThat(reader.readRows(file)).singleElement().satisfies(row -> {
            assertThat(row.rowNumber()).isEqualTo(2);
            assertThat(row.productName()).isEqualTo("테스트 상품");
            assertThat(row.myCategoryCode()).isEqualTo("MY1");
        });
    }

    @Test
    void readsRowsFromMultipleFiles() throws Exception {
        var first = excel("상품명", "마이카테", new String[][]{{"상품1", "MY1"}});
        var second = excel("마이카테고리", "상품명", new String[][]{{"MY2", "상품2"}});

        assertThat(reader.readRows(List.of(first, second)))
                .extracting(TrainingProductExcelReader.TrainingProductExcelRow::productName)
                .containsExactly("상품1", "상품2");
    }

    @Test
    void rejectsMissingRequiredHeader() throws Exception {
        var file = excel("마이카테", "잘못된헤더", new String[][]{});

        assertThatThrownBy(() -> reader.readRows(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("상품명");
    }

    @Test
    void rejectsInvalidFiles() {
        var invalidFile = new MockMultipartFile("files", "products.xls", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> reader.validateFiles(List.of(invalidFile)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".xlsx");
    }

    private MockMultipartFile excel(String firstHeader, String secondHeader, String[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(firstHeader);
            header.createCell(1).setCellValue(secondHeader);
            for (int index = 0; index < rows.length; index++) {
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(1).setCellValue(rows[index][1]);
            }
            workbook.write(output);
            return new MockMultipartFile("files", "products.xlsx", "application/octet-stream", output.toByteArray());
        }
    }
}
