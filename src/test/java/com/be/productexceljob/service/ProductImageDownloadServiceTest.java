package com.be.productexceljob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.be.productexceljob.dto.ProductImageDownloadPrepareResponse;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductImageDownloadServiceTest {
    private final ProductImageDownloadService service = new ProductImageDownloadService(null);

    @Test
    void preparesValidImagesAndReportsInvalidRows() throws Exception {
        MockMultipartFile file = imageDownloadExcel();

        ProductImageDownloadPrepareResponse response = service.prepareImageDownloads(file);

        assertEquals(2, response.imageCount());
        assertEquals(1, response.failedCount());
        assertEquals("100.jpg", response.images().get(0).filename());
        assertEquals("100_2.jpg", response.images().get(1).filename());
        assertEquals(3, response.failures().getFirst().rowNumber());
        assertEquals("P-2", response.failures().getFirst().name());
    }

    private MockMultipartFile imageDownloadExcel() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("상품");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("목록이미지1");
            header.createCell(1).setCellValue("상품코드");
            header.createCell(2).setCellValue("제품번호");

            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("https://example.com/first image.png");
            first.createCell(1).setCellValue("P-1");
            first.createCell(2).setCellValue("100");

            Row invalid = sheet.createRow(2);
            invalid.createCell(1).setCellValue("P-2");

            Row duplicate = sheet.createRow(3);
            duplicate.createCell(0).setCellValue("https://example.com/second.png");
            duplicate.createCell(2).setCellValue("100");

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
