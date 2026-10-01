package com.be.trainingproduct.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.excel.ExcelCellReader;
import com.be.global.excel.ExcelHeaderLookup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class TrainingProductExcelReader {
    private static final List<String> PRODUCT_NAME_HEADERS = List.of("상품명");
    private static final List<String> MY_CATEGORY_HEADERS = List.of("마이카테", "마이카테고리", "마이카테고리코드");

    public void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalid("기존 상품 엑셀 파일을 하나 이상 업로드해 주세요.");
        }

        boolean invalidFile = files.stream().anyMatch(file ->
                file == null
                        || file.isEmpty()
                        || file.getOriginalFilename() == null
                        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")
        );
        if (invalidFile) {
            throw invalid("기존 상품 파일은 비어 있지 않은 .xlsx 형식이어야 합니다.");
        }
    }

    public List<TrainingProductExcelRow> readRows(MultipartFile file) {
        return readRows(file, "상품 엑셀 파일을 읽지 못했습니다.");
    }

    public List<TrainingProductExcelRow> readRows(List<MultipartFile> files) {
        List<TrainingProductExcelRow> rows = new ArrayList<>();
        for (MultipartFile file : files) {
            rows.addAll(readRows(file, "기존 상품 엑셀 파일을 읽지 못했습니다."));
        }
        return rows;
    }

    private List<TrainingProductExcelRow> readRows(MultipartFile file, String failureMessage) {
        List<TrainingProductExcelRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalid("상품 엑셀에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            TrainingProductColumns columns = resolveColumns(sheet);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String productName = ExcelCellReader.readTrimmed(
                        row,
                        columns.productNameColumnIndex(),
                        formatter
                );
                if (productName.isBlank()) {
                    continue;
                }
                String myCategoryCode = ExcelCellReader.readTrimmed(
                        row,
                        columns.myCategoryColumnIndex(),
                        formatter
                );
                rows.add(new TrainingProductExcelRow(rowIndex + 1, productName, myCategoryCode));
            }
            return rows;
        } catch (IOException error) {
            throw invalid(failureMessage);
        }
    }

    private TrainingProductColumns resolveColumns(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw invalid("기존 상품 엑셀 파일의 헤더 행이 비어 있습니다.");
        }

        ExcelHeaderLookup headers = ExcelHeaderLookup.from(headerRow);
        return new TrainingProductColumns(
                findRequiredColumnIndex(headers, PRODUCT_NAME_HEADERS, "상품명"),
                findRequiredColumnIndex(headers, MY_CATEGORY_HEADERS, "마이카테고리")
        );
    }

    private int findRequiredColumnIndex(
            ExcelHeaderLookup headerLookup,
            List<String> acceptedHeaders,
            String displayName
    ) {
        int index = headerLookup.findFirstNormalized(acceptedHeaders);
        if (index < 0) {
            throw invalid("기존 상품 엑셀 파일에 필요한 헤더가 없습니다: " + displayName);
        }
        return index;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }

    public record TrainingProductExcelRow(
            int rowNumber,
            String productName,
            String myCategoryCode
    ) {
    }

    private record TrainingProductColumns(
            int productNameColumnIndex,
            int myCategoryColumnIndex
    ) {
    }
}
