package com.be.keywordjob.service;

import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.dto.MyCategoryMatchStatus;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.keywordjob.dto.ExcelDownloadResult;
import com.be.keywordjob.dto.ImageDownloadResponse;
import com.be.keywordjob.dto.ImageZipDownloadResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KeywordExcelFillService {
    private static final int KEYWORD_COLUMN_INDEX = 11; // L
    private static final int MY_CATEGORY_COLUMN_INDEX = 19; // T
    private static final int UNMAPPED_NAVER_CATEGORY_COLUMN_INDEX = 20; // U
    private static final int DEFAULT_KEYWORD_COUNT = 30;
    private static final String KEYWORD_HEADER = "\uD0A4\uC6CC\uB4DC";
    private static final String MY_CATEGORY_HEADER = "\uB9C8\uC774\uCE74\uD14C";
    private static final String UNMAPPED_NAVER_CATEGORY_HEADER = "\uB124\uC774\uBC84\uCE74\uD14C";
    private static final String IMAGE_URL_COLUMN = "\uBAA9\uB85D\uC774\uBBF8\uC9C01";
    private static final String PRODUCT_CODE_COLUMN = "\uC0C1\uD488\uCF54\uB4DC";
    private static final String PRODUCT_NUMBER_COLUMN = "\uC81C\uD488\uBC88\uD638";
    private static final String NO_CATEGORY_MATCH = "\uB9E4\uCE6D\uC5C6\uC74C";
    private static final String NO_MY_CATEGORY_MAPPING = "\uB9C8\uC774\uCE74\uD14C \uC5C6\uC74C";

    private final CategoryMatcherService categoryMatcherService;
    private final KeywordJobUploadService keywordJobUploadService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    public ExcelDownloadResult fillAndDownload(
            MultipartFile file,
            String productNameColumn,
            String categoryColumn,
            Integer keywordCount,
            String userKey
    ) {
        keywordJobUploadService.validate(file, productNameColumn, keywordCount);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int productNameColumnIndex = findRequiredColumnIndex(headerRow, productNameColumn);
            int categoryColumnIndex = findOptionalColumnIndex(headerRow, categoryColumn);
            ensureHeader(headerRow, KEYWORD_COLUMN_INDEX, KEYWORD_HEADER);
            ensureHeader(headerRow, MY_CATEGORY_COLUMN_INDEX, MY_CATEGORY_HEADER);
            ensureHeader(headerRow, UNMAPPED_NAVER_CATEGORY_COLUMN_INDEX, UNMAPPED_NAVER_CATEGORY_HEADER);

            int resolvedKeywordCount = keywordCount == null ? DEFAULT_KEYWORD_COUNT : keywordCount;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String productName = readCell(row, productNameColumnIndex, formatter);
                String category = categoryColumnIndex < 0 ? "" : readCell(row, categoryColumnIndex, formatter);
                if (productName.isBlank()) {
                    continue;
                }

                MyCategoryMatchResult myCategoryResult = categoryMatcherService.findMyCategoryCode(productName, userKey);
                String myCategory = resolveMyCategory(myCategoryResult);
                List<String> keywords = generateKeywords(productName, category, myCategory, resolvedKeywordCount);

                row.createCell(KEYWORD_COLUMN_INDEX).setCellValue(String.join(", ", keywords));
                row.createCell(MY_CATEGORY_COLUMN_INDEX).setCellValue(myCategory);
                writeUnmappedNaverCategory(row, myCategoryResult);
            }

            workbook.write(outputStream);
            String filename = buildDownloadFilename(file.getOriginalFilename());
            return new ExcelDownloadResult(filename, outputStream.toByteArray());
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process excel file.");
        }
    }

    public ImageDownloadResponse downloadImages(MultipartFile file, String imageOutputDir) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int imageUrlColumnIndex = findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN);
            int productCodeColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN);
            int productNumberColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN);
            Path imageDirectory = resolveImageDirectory(imageOutputDir);
            Files.createDirectories(imageDirectory);

            int savedCount = 0;
            int failedCount = 0;
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                boolean saved = downloadImageIfPresent(
                        row,
                        formatter,
                        imageUrlColumnIndex,
                        productCodeColumnIndex,
                        productNumberColumnIndex,
                        imageDirectory,
                        rowIndex + 1
                );

                if (saved) {
                    savedCount++;
                } else {
                    failedCount++;
                }
            }

            return new ImageDownloadResponse(
                    savedCount,
                    failedCount,
                    imageDirectory.toString(),
                    "Images downloaded."
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process image download.");
        }
    }

    public ImageZipDownloadResult downloadImagesAsZip(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Excel header row is empty.");
            }

            int imageUrlColumnIndex = findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN);
            int productCodeColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN);
            int productNumberColumnIndex = findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN);

            int savedCount = 0;
            int failedCount = 0;
            Set<String> entryNames = new LinkedHashSet<>();
            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String imageUrl = readCell(row, imageUrlColumnIndex, formatter);
                if (!isHttpUrl(imageUrl)) {
                    failedCount++;
                    continue;
                }

                String productCode = productCodeColumnIndex < 0 ? "" : readCell(row, productCodeColumnIndex, formatter);
                String productNumber = productNumberColumnIndex < 0 ? "" : readCell(row, productNumberColumnIndex, formatter);
                String filenameBase = !productCode.isBlank() ? productCode : (!productNumber.isBlank() ? productNumber : "row_" + (rowIndex + 1));
                String entryName = uniqueEntryName(entryNames, safeFilename(filenameBase), imageExtension(imageUrl));

                try {
                    byte[] imageBytes = fetchImage(imageUrl);
                    ZipEntry entry = new ZipEntry(entryName);
                    zipOutputStream.putNextEntry(entry);
                    zipOutputStream.write(imageBytes);
                    zipOutputStream.closeEntry();
                    savedCount++;
                } catch (Exception ignored) {
                    failedCount++;
                }
            }

            zipOutputStream.finish();
            return new ImageZipDownloadResult(
                    buildImageZipFilename(file.getOriginalFilename()),
                    outputStream.toByteArray(),
                    savedCount,
                    failedCount
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Failed to process image zip download.");
        }
    }

    private boolean downloadImageIfPresent(
            Row row,
            DataFormatter formatter,
            int imageUrlColumnIndex,
            int productCodeColumnIndex,
            int productNumberColumnIndex,
            Path imageDirectory,
            int excelRowNumber
    ) {
        String imageUrl = readCell(row, imageUrlColumnIndex, formatter);
        if (!isHttpUrl(imageUrl)) {
            return false;
        }

        String productCode = productCodeColumnIndex < 0 ? "" : readCell(row, productCodeColumnIndex, formatter);
        String productNumber = productNumberColumnIndex < 0 ? "" : readCell(row, productNumberColumnIndex, formatter);
        String filenameBase = !productCode.isBlank() ? productCode : (!productNumber.isBlank() ? productNumber : "row_" + excelRowNumber);
        Path targetPath = imageDirectory.resolve(safeFilename(filenameBase) + imageExtension(imageUrl));

        try {
            Files.write(targetPath, fetchImage(imageUrl));
            return true;
        } catch (Exception ignored) {
            return false;
        }
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

    private int findRequiredColumnIndex(Row headerRow, String columnName) {
        int index = findOptionalColumnIndex(headerRow, columnName);
        if (index < 0) {
            throw new BusinessException(ErrorCode.COLUMN_NOT_FOUND, "Column not found: " + columnName);
        }
        return index;
    }

    private int findOptionalColumnIndex(Row headerRow, String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return -1;
        }

        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim();
            if (value.equals(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private void ensureHeader(Row headerRow, int columnIndex, String value) {
        Cell cell = headerRow.getCell(columnIndex);
        if (cell == null) {
            cell = headerRow.createCell(columnIndex);
        }
        cell.setCellValue(value);
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String inferMyCategory(String productName, String category) {
        if (category != null && !category.isBlank()) {
            String[] parts = category.split(">");
            String last = parts[parts.length - 1].trim();
            if (!last.isBlank()) {
                return last;
            }
        }

        String text = productName.toLowerCase(Locale.ROOT);
        if (text.contains("\uC5FD\uC11C")) return "\uC5FD\uC11C";
        if (text.contains("\uB2E4\uC774\uC5B4\uB9AC")) return "\uB2E4\uC774\uC5B4\uB9AC";
        if (text.contains("\uAC00\uACC4\uBD80")) return "\uAC00\uACC4\uBD80";
        if (text.contains("\uBC14\uC778\uB354")) return "\uBC14\uC778\uB354";
        if (text.contains("\uD0A4\uBCF4\uB4DC")) return "\uD0A4\uBCF4\uB4DC";
        if (text.contains("\uC2A4\uD2F0\uCEE4") || text.contains("\uC52C")) return "\uC2A4\uD2F0\uCEE4";
        return "\uAE30\uD0C0";
    }

    private String resolveMyCategory(MyCategoryMatchResult result) {
        if (result.status() == MyCategoryMatchStatus.MATCHED) {
            return result.myCategoryCode();
        }
        if (result.status() == MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING) {
            return NO_MY_CATEGORY_MAPPING;
        }
        return NO_CATEGORY_MATCH;
    }

    private void writeUnmappedNaverCategory(Row row, MyCategoryMatchResult result) {
        if (result.status() == MyCategoryMatchStatus.NO_MY_CATEGORY_MAPPING && result.naverCategory() != null) {
            row.createCell(UNMAPPED_NAVER_CATEGORY_COLUMN_INDEX).setCellValue(result.naverCategory());
            return;
        }
        row.createCell(UNMAPPED_NAVER_CATEGORY_COLUMN_INDEX).setCellValue("");
    }

    private List<String> generateKeywords(String productName, String category, String myCategory, int keywordCount) {
        Set<String> keywords = new LinkedHashSet<>();
        List<String> tokens = tokenize(productName);

        addKeyword(keywords, myCategory);
        addKeyword(keywords, myCategory + "\uCD94\uCC9C");
        addKeyword(keywords, myCategory + "\uC120\uBB3C");
        addKeyword(keywords, myCategory + "\uBB38\uAD6C");
        addKeyword(keywords, "\uAC10\uC131" + myCategory);
        addKeyword(keywords, "\uADC0\uC5EC\uC6B4" + myCategory);
        addKeyword(keywords, "\uB514\uC790\uC778" + myCategory);
        addKeyword(keywords, "\uD559\uC0DD" + myCategory);
        addKeyword(keywords, "\uC0AC\uBB34\uC6A9" + myCategory);

        for (String token : tokens) {
            addKeyword(keywords, token + myCategory);
            addKeyword(keywords, token);
        }

        if (category != null && !category.isBlank()) {
            for (String token : tokenize(category)) {
                addKeyword(keywords, token + myCategory);
            }
        }

        return keywords.stream()
                .limit(keywordCount)
                .toList();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] rawTokens = text.split("[\\s_/(),\\[\\]-]+");
        List<String> tokens = new ArrayList<>();
        for (String rawToken : rawTokens) {
            String token = rawToken.replaceAll("[^\uAC00-\uD7A3A-Za-z0-9]", "").trim();
            if (token.length() >= 2 && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void addKeyword(Set<String> keywords, String keyword) {
        if (keyword == null) {
            return;
        }
        String cleaned = keyword.replaceAll("\\s+", "").trim();
        if (cleaned.length() >= 2 && cleaned.length() <= 20) {
            keywords.add(cleaned);
        }
    }

    private Path resolveImageDirectory(String imageOutputDir) {
        if (imageOutputDir != null && !imageOutputDir.isBlank()) {
            return Path.of(imageOutputDir).toAbsolutePath().normalize();
        }
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("product-images");
    }

    private String imageExtension(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            String extension = path.substring(dotIndex).toLowerCase(Locale.ROOT);
            if (extension.matches("\\.(jpg|jpeg|png|webp|gif)")) {
                return extension;
            }
        }
        return ".jpg";
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

    private String buildDownloadFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input.xlsx"
                : originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "keyword_result_" + baseName;
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String buildImageZipFilename(String originalFilename) {
        String baseName = originalFilename == null || originalFilename.isBlank()
                ? "input"
                : originalFilename.replaceAll("\\.(xlsx|xls)$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "product_images_" + baseName + ".zip";
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Please upload an excel file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "Invalid excel file format.");
        }
    }
}
