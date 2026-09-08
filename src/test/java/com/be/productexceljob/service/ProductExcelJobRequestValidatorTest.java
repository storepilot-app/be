package com.be.productexceljob.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductExcelJobRequestValidatorTest {
    private final ProductExcelJobRequestValidator validator = new ProductExcelJobRequestValidator();

    @Test
    void acceptsUpToOneThousandFiveHundredProducts() throws IOException {
        MockMultipartFile file = createExcelFile(1_500);

        int productCount = validator.validate(file, "상품명", 30);

        assertThat(productCount).isEqualTo(1_500);
    }

    @Test
    void rejectsMoreThanOneThousandFiveHundredProducts() throws IOException {
        MockMultipartFile file = createExcelFile(1_501);

        assertThatThrownBy(() -> validator.validate(file, "상품명", 30))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_EXCEL_JOB_LIMIT_EXCEEDED);
                    assertThat(error.getMessage()).contains("1,500개");
                });
    }

    private MockMultipartFile createExcelFile(int productCount) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("상품명");
            for (int index = 1; index <= productCount; index++) {
                sheet.createRow(index).createCell(0).setCellValue("상품 " + index);
            }
            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }
}
