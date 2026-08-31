package com.be.productimage.excel;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productimage.dto.ProductImageDownloadFailure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ProductImageFailureExcelWriter {
    public byte[] write(List<ProductImageDownloadFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "저장할 이미지 다운로드 실패 내역이 없습니다.");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("이미지 다운로드 실패");
            writeHeader(sheet);
            writeFailures(sheet, failures);
            applyLayout(sheet);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드 실패 목록 엑셀을 생성하지 못했습니다.");
        }
    }

    private void writeHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("엑셀 행");
        headerRow.createCell(1).setCellValue("파일명");
        headerRow.createCell(2).setCellValue("이미지 URL");
        headerRow.createCell(3).setCellValue("실패 사유");
    }

    private void writeFailures(Sheet sheet, List<ProductImageDownloadFailure> failures) {
        for (int index = 0; index < failures.size(); index++) {
            ProductImageDownloadFailure failure = failures.get(index);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(failure.rowNumber());
            row.createCell(1).setCellValue(safeCellValue(failure.name()));
            row.createCell(2).setCellValue(safeCellValue(failure.url()));
            row.createCell(3).setCellValue(safeCellValue(failure.reason()));
        }
    }

    private void applyLayout(Sheet sheet) {
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 25 * 256);
        sheet.setColumnWidth(2, 70 * 256);
        sheet.setColumnWidth(3, 50 * 256);
        sheet.createFreezePane(0, 1);
    }

    private String safeCellValue(String value) {
        return value == null ? "" : value;
    }
}
