package com.be.productimage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.productimage.client.RemoteImageClient;
import com.be.productimage.dto.ProductImageDownloadPrepareResponse;
import com.be.productimage.excel.ProductImageDownloadExcelReader;
import com.be.productimage.image.JpegImageCompressor;
import com.be.productimage.image.ProductImageResizer;
import com.be.productimage.validation.RemoteImageUrlValidator;
import com.be.userusage.service.UserUsageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductImageDownloadServiceTest {
    private final ProductImageDownloadService service = new ProductImageDownloadService(
            new ProductImageDownloadExcelReader(new RemoteImageUrlValidator()),
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

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

    @Test
    void recordsUsageAfterImageDownload() throws Exception {
        RemoteImageClient remoteImageClient = mock(RemoteImageClient.class);
        ProductImageResizer imageResizer = mock(ProductImageResizer.class);
        JpegImageCompressor imageCompressor = mock(JpegImageCompressor.class);
        UserUsageService userUsageService = mock(UserUsageService.class);
        byte[] originalImage = {1, 2, 3};
        byte[] downloadedImage = {4, 5, 6};
        BufferedImage resizedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        when(remoteImageClient.download("https://example.com/image.png")).thenReturn(originalImage);
        when(imageResizer.resizeToSquare(originalImage)).thenReturn(resizedImage);
        when(imageCompressor.compress(resizedImage, originalImage.length, 80)).thenReturn(downloadedImage);
        ProductImageDownloadService downloadService = new ProductImageDownloadService(
                null,
                remoteImageClient,
                imageResizer,
                null,
                imageCompressor,
                null,
                null,
                userUsageService
        );

        byte[] result = downloadService.downloadImage(
                "https://example.com/image.png",
                80,
                7L,
                false
        );

        assertArrayEquals(downloadedImage, result);
        verify(userUsageService).recordImageDownloads(7L, 1);
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
