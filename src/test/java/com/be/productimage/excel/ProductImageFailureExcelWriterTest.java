package com.be.productimage.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.be.global.exception.BusinessException;
import com.be.productimage.dto.ProductImageDownloadFailure;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class ProductImageFailureExcelWriterTest {
    private final ProductImageFailureExcelWriter writer = new ProductImageFailureExcelWriter();

    @Test
    void writesFailureRows() throws Exception {
        byte[] content = writer.write(List.of(
                new ProductImageDownloadFailure(3, "P-2", "invalid-url", "올바르지 않은 URL")
        ));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("이미지 URL", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("P-2", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("올바르지 않은 URL", sheet.getRow(1).getCell(3).getStringCellValue());
        }
    }

    @Test
    void rejectsEmptyFailures() {
        assertThrows(BusinessException.class, () -> writer.write(List.of()));
    }
}
