package com.be.productimage.excel;

import static com.be.productimage.excel.ProductImageDownloadLayout.IMAGE_URL_COLUMN;
import static com.be.productimage.excel.ProductImageDownloadLayout.PRODUCT_CODE_COLUMN;
import static com.be.productimage.excel.ProductImageDownloadLayout.PRODUCT_NUMBER_COLUMN;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productimage.dto.ProductImageDownloadFailure;
import com.be.productimage.dto.ProductImageDownloadItem;
import com.be.productimage.dto.ProductImageDownloadPrepareResponse;
import com.be.productimage.validation.RemoteImageUrlValidator;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ProductImageDownloadExcelReader {
    private static final String PRODUCT_IMAGE_EXTENSION = ".jpg";

    private final RemoteImageUrlValidator urlValidator;

    public ProductImageDownloadPrepareResponse read(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            ProductImageDownloadSheetContext sheetContext = prepareSheet(sheet);
            ProductImageDownloadRows rows = readRows(sheet, sheetContext);
            return new ProductImageDownloadPrepareResponse(
                    rows.images().size(),
                    rows.failures().size(),
                    rows.images(),
                    rows.failures()
            );
        } catch (BusinessException error) {
            throw error;
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드 대상 엑셀을 읽지 못했습니다.");
        }
    }

    private ProductImageDownloadSheetContext prepareSheet(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 헤더 행이 비어 있습니다.");
        }

        return new ProductImageDownloadSheetContext(
                findRequiredColumnIndex(headerRow, IMAGE_URL_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_CODE_COLUMN),
                findOptionalColumnIndex(headerRow, PRODUCT_NUMBER_COLUMN)
        );
    }

    private ProductImageDownloadRows readRows(Sheet sheet, ProductImageDownloadSheetContext sheetContext) {
        Set<String> entryNames = new LinkedHashSet<>();
        List<ProductImageDownloadItem> images = new ArrayList<>();
        List<ProductImageDownloadFailure> failures = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String imageUrl = readCell(row, sheetContext.imageUrlColumnIndex(), formatter);
            String productCode = sheetContext.productCodeColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productCodeColumnIndex(), formatter);
            String productNumber = sheetContext.productNumberColumnIndex() < 0
                    ? ""
                    : readCell(row, sheetContext.productNumberColumnIndex(), formatter);
            String filenameBase = !productNumber.isBlank()
                    ? productNumber
                    : (!productCode.isBlank() ? productCode : "row_" + (rowIndex + 1));

            URI imageUri;
            try {
                imageUri = urlValidator.parseHttpUri(imageUrl);
            } catch (BusinessException error) {
                failures.add(new ProductImageDownloadFailure(
                        rowIndex + 1,
                        filenameBase,
                        imageUrl,
                        error.getMessage()
                ));
                continue;
            }

            String entryName = uniqueEntryName(entryNames, safeFilename(filenameBase), PRODUCT_IMAGE_EXTENSION);
            images.add(new ProductImageDownloadItem(rowIndex + 1, filenameBase, entryName, imageUri.toString()));
        }

        return new ProductImageDownloadRows(images, failures);
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
            if (formatter.formatCellValue(cell).trim().equals(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
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

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일을 업로드해주세요.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "엑셀 파일 형식이 올바르지 않습니다.");
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
}
