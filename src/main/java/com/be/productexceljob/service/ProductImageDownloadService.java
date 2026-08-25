package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductImageDownloadLayout.IMAGE_URL_COLUMN;
import static com.be.productexceljob.excel.ProductImageDownloadLayout.PRODUCT_CODE_COLUMN;
import static com.be.productexceljob.excel.ProductImageDownloadLayout.PRODUCT_NUMBER_COLUMN;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productexceljob.dto.ProductImageDownloadFailure;
import com.be.productexceljob.dto.ProductImageDownloadItem;
import com.be.productexceljob.dto.ProductImageDownloadPrepareResponse;
import com.be.watermark.domain.WatermarkPosition;
import com.be.watermark.service.UserWatermarkService;
import com.be.watermark.service.UserWatermarkService.WatermarkImage;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductImageDownloadService {
    private static final int PRODUCT_IMAGE_SIZE = 1000;
    private static final String PRODUCT_IMAGE_EXTENSION = ".jpg";
    private static final int MIN_TARGET_SIZE_PERCENT = 30;
    private static final int MAX_TARGET_SIZE_PERCENT = 100;
    private static final float MIN_JPEG_QUALITY = 0.1f;
    private static final float MAX_JPEG_QUALITY = 1.0f;
    private static final int JPEG_QUALITY_SEARCH_ITERATIONS = 8;

    private final UserWatermarkService userWatermarkService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ProductImageDownloadPrepareResponse prepareImageDownloads(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            ProductImageDownloadSheetContext sheetContext = prepareImageDownloadSheet(sheet);
            ProductImageDownloadRows rows = readImageDownloadRows(sheet, sheetContext);

            return new ProductImageDownloadPrepareResponse(
                    rows.images().size(),
                    rows.failures().size(),
                    rows.images(),
                    rows.failures()
            );
        } catch (BusinessException error) {
            throw error;
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to read image download targets.");
        }
    }

    public byte[] downloadImage(
            String imageUrl,
            Integer targetSizePercent,
            Long userId,
            boolean applyWatermark
    ) {
        if (!isHttpUrl(imageUrl)) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 URL이 비어 있거나 올바르지 않습니다.");
        }
        if (targetSizePercent == null
                || targetSizePercent < MIN_TARGET_SIZE_PERCENT
                || targetSizePercent > MAX_TARGET_SIZE_PERCENT) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "목표 용량 비율은 30~100 사이여야 합니다.");
        }

        try {
            byte[] originalImage = fetchImage(imageUrl);
            BufferedImage resizedImage = resizeImageToSquare(originalImage);
            if (applyWatermark) {
                applyWatermark(resizedImage, userWatermarkService.getRequiredImage(userId));
            }
            long targetBytes = Math.max(1L, Math.round(originalImage.length * targetSizePercent / 100.0));
            return compressJpegToTarget(resizedImage, targetBytes);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드가 중단되었습니다.");
        }
    }

    public byte[] createImageFailureExcel(List<ProductImageDownloadFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "저장할 이미지 다운로드 실패 내역이 없습니다.");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("이미지 다운로드 실패");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("엑셀 행");
            headerRow.createCell(1).setCellValue("파일명");
            headerRow.createCell(2).setCellValue("이미지 URL");
            headerRow.createCell(3).setCellValue("실패 사유");

            for (int index = 0; index < failures.size(); index++) {
                ProductImageDownloadFailure failure = failures.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(failure.rowNumber());
                row.createCell(1).setCellValue(safeCellValue(failure.name()));
                row.createCell(2).setCellValue(safeCellValue(failure.url()));
                row.createCell(3).setCellValue(safeCellValue(failure.reason()));
            }

            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 25 * 256);
            sheet.setColumnWidth(2, 70 * 256);
            sheet.setColumnWidth(3, 50 * 256);
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드 실패 목록 엑셀을 생성하지 못했습니다.");
        }
    }

    private ProductImageDownloadSheetContext prepareImageDownloadSheet(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
        }

        return new ProductImageDownloadSheetContext(
                findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN)
        );
    }

    private ProductImageDownloadRows readImageDownloadRows(
            Sheet sheet,
            ProductImageDownloadSheetContext sheetContext
    ) {
        Set<String> entryNames = new LinkedHashSet<>();
        List<ProductImageDownloadItem> images = new ArrayList<>();
        List<ProductImageDownloadFailure> failures = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String imageUrl = normalizeImageUrl(readCell(row, sheetContext.imageUrlColumnIndex(), formatter));
            String productCode = sheetContext.productCodeColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productCodeColumnIndex(), formatter);
            String productNumber = sheetContext.productNumberColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productNumberColumnIndex(), formatter);
            String filenameBase = !productNumber.isBlank()
                    ? productNumber
                    : (!productCode.isBlank() ? productCode : "row_" + (rowIndex + 1));
            if (!isHttpUrl(imageUrl)) {
                failures.add(new ProductImageDownloadFailure(
                        rowIndex + 1,
                        filenameBase,
                        imageUrl,
                        "이미지 URL이 비어 있거나 올바르지 않습니다."
                ));
                continue;
            }

            String entryName = uniqueEntryName(entryNames, safeFilename(filenameBase), PRODUCT_IMAGE_EXTENSION);
            images.add(new ProductImageDownloadItem(rowIndex + 1, filenameBase, entryName, imageUrl));
        }

        return new ProductImageDownloadRows(images, failures);
    }

    private byte[] fetchImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "StorePilot/1.0")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Image request failed with status " + response.statusCode());
        }
        return response.body();
    }

    private BufferedImage resizeImageToSquare(byte[] imageBytes) throws IOException {
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (sourceImage == null) {
            throw new IOException("지원하지 않는 이미지 형식입니다.");
        }

        BufferedImage resizedImage = new BufferedImage(
                PRODUCT_IMAGE_SIZE,
                PRODUCT_IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PRODUCT_IMAGE_SIZE, PRODUCT_IMAGE_SIZE);

            double scale = Math.min(
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getWidth(),
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getHeight()
            );
            int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
            int x = (PRODUCT_IMAGE_SIZE - width) / 2;
            int y = (PRODUCT_IMAGE_SIZE - height) / 2;
            graphics.drawImage(sourceImage, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }

        return resizedImage;
    }

    private void applyWatermark(BufferedImage productImage, WatermarkImage watermark) throws IOException {
        BufferedImage watermarkImage = ImageIO.read(new ByteArrayInputStream(watermark.content()));
        if (watermarkImage == null) {
            throw new IOException("저장된 워터마크 이미지를 읽지 못했습니다.");
        }

        int targetWidth = Math.max(1, productImage.getWidth() * watermark.sizePercent() / 100);
        int targetHeight = Math.max(1, (int) Math.round(
                targetWidth * watermarkImage.getHeight() / (double) watermarkImage.getWidth()
        ));
        if (targetHeight > productImage.getHeight()) {
            targetHeight = productImage.getHeight();
            targetWidth = Math.max(1, (int) Math.round(
                    targetHeight * watermarkImage.getWidth() / (double) watermarkImage.getHeight()
            ));
        }

        int margin = Math.max(10, productImage.getWidth() / 50);
        WatermarkCoordinates coordinates = watermarkCoordinates(
                productImage.getWidth(),
                productImage.getHeight(),
                targetWidth,
                targetHeight,
                margin,
                watermark.position()
        );

        Graphics2D graphics = productImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER,
                    watermark.opacity() / 100.0f
            ));
            graphics.drawImage(
                    watermarkImage,
                    coordinates.x(),
                    coordinates.y(),
                    targetWidth,
                    targetHeight,
                    null
            );
        } finally {
            graphics.dispose();
        }
    }

    private WatermarkCoordinates watermarkCoordinates(
            int imageWidth,
            int imageHeight,
            int watermarkWidth,
            int watermarkHeight,
            int margin,
            WatermarkPosition position
    ) {
        return switch (position) {
            case TOP_LEFT -> new WatermarkCoordinates(margin, margin);
            case TOP_RIGHT -> new WatermarkCoordinates(imageWidth - watermarkWidth - margin, margin);
            case CENTER -> new WatermarkCoordinates(
                    (imageWidth - watermarkWidth) / 2,
                    (imageHeight - watermarkHeight) / 2
            );
            case BOTTOM_LEFT -> new WatermarkCoordinates(margin, imageHeight - watermarkHeight - margin);
            case BOTTOM_RIGHT -> new WatermarkCoordinates(
                    imageWidth - watermarkWidth - margin,
                    imageHeight - watermarkHeight - margin
            );
        };
    }

    private byte[] compressJpegToTarget(BufferedImage image, long targetBytes) throws IOException {
        byte[] highestQuality = writeJpeg(image, MAX_JPEG_QUALITY);
        if (highestQuality.length <= targetBytes) {
            return highestQuality;
        }

        byte[] lowestQuality = writeJpeg(image, MIN_JPEG_QUALITY);
        if (lowestQuality.length > targetBytes) {
            return lowestQuality;
        }

        byte[] bestResult = lowestQuality;
        float low = MIN_JPEG_QUALITY;
        float high = MAX_JPEG_QUALITY;
        for (int iteration = 0; iteration < JPEG_QUALITY_SEARCH_ITERATIONS; iteration++) {
            float quality = (low + high) / 2;
            byte[] candidate = writeJpeg(image, quality);
            if (candidate.length <= targetBytes) {
                bestResult = candidate;
                low = quality;
            } else {
                high = quality;
            }
        }
        return bestResult;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 이미지 인코더를 찾지 못했습니다.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private int findRequiredColumnIndex(Row headerRow, String columnName) {
        int index = findOptionalColumnIndex(headerRow, columnName);
        if (index < 0) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "Column not found: " + columnName);
        }
        return index;
    }

    private int findOptionalColumnIndex(Row headerRow, String columnName) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim();
            if (value.equals(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalizeImageUrl(String imageUrl) {
        return imageUrl == null ? "" : imageUrl.trim().replace(" ", "%20");
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String uniqueEntryName(Set<String> entryNames, String filenameBase, String extension) {
        String normalizedBase = filenameBase == null || filenameBase.isBlank() ? "image" : filenameBase;
        String entryName = normalizedBase + extension;
        int sequence = 2;
        while (entryNames.contains(entryName)) {
            entryName = normalizedBase + "_" + sequence + extension;
            sequence++;
        }
        entryNames.add(entryName);
        return entryName;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String safeCellValue(String value) {
        return value == null ? "" : value;
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Please upload an excel file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Invalid excel file format.");
        }
    }

    private record ProductImageDownloadSheetContext(
            int imageUrlColumnIndex,
            int productCodeColumnIndex,
            int productNumberColumnIndex
    ) {
    }

    private record ProductImageDownloadRows(
            List<ProductImageDownloadItem> images,
            List<ProductImageDownloadFailure> failures
    ) {
    }

    private record WatermarkCoordinates(int x, int y) {
    }
}
