package com.be.keywordjob.excel;

import com.be.keywordjob.keyword.KeywordCandidateRanker.ScoredKeyword;
import com.be.keywordjob.keyword.KeywordDetailEntry;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

@Component
public class KeywordDetailSheetWriter {
    public static final String SHEET_NAME = "키워드 상세";

    private static final List<String> HEADERS = List.of(
            "원본 행",
            "상품명",
            "네이버 카테고리",
            "키워드",
            "순위",
            "최종점수",
            "상품명점수",
            "카테고리점수",
            "근거점수",
            "길이점수",
            "생성 근거"
    );

    public void write(Workbook workbook, List<KeywordDetailEntry> entries) {
        int existingSheetIndex = workbook.getSheetIndex(SHEET_NAME);
        if (existingSheetIndex >= 0) {
            workbook.removeSheetAt(existingSheetIndex);
        }

        Sheet sheet = workbook.createSheet(SHEET_NAME);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle scoreStyle = createScoreStyle(workbook);
        writeHeader(sheet, headerStyle);

        int rowIndex = 1;
        for (KeywordDetailEntry entry : entries) {
            writeEntry(sheet.createRow(rowIndex++), entry, scoreStyle);
        }

        applyLayout(sheet, Math.max(1, rowIndex));
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.size(); index++) {
            header.createCell(index).setCellValue(HEADERS.get(index));
            header.getCell(index).setCellStyle(headerStyle);
        }
    }

    private void writeEntry(Row row, KeywordDetailEntry entry, CellStyle scoreStyle) {
        ScoredKeyword keyword = entry.scoredKeyword();
        row.createCell(0).setCellValue(entry.sourceRow());
        row.createCell(1).setCellValue(entry.productName());
        row.createCell(2).setCellValue(entry.naverCategory());
        row.createCell(3).setCellValue(keyword.keyword());
        row.createCell(4).setCellValue(entry.rank());
        writeScore(row, 5, keyword.finalScore(), scoreStyle);
        writeScore(row, 6, keyword.titleScore(), scoreStyle);
        writeScore(row, 7, keyword.categoryScore(), scoreStyle);
        writeScore(row, 8, keyword.evidenceScore(), scoreStyle);
        writeScore(row, 9, keyword.specificityScore(), scoreStyle);
        row.createCell(10).setCellValue(String.join(", ", entry.reasons()));
    }

    private void writeScore(Row row, int columnIndex, double value, CellStyle scoreStyle) {
        row.createCell(columnIndex).setCellValue(value);
        row.getCell(columnIndex).setCellStyle(scoreStyle);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createScoreStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
        return style;
    }

    private void applyLayout(Sheet sheet, int rowCount) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, HEADERS.size() - 1));
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 40 * 256);
        sheet.setColumnWidth(2, 55 * 256);
        sheet.setColumnWidth(3, 25 * 256);
        sheet.setColumnWidth(4, 8 * 256);
        for (int index = 5; index <= 9; index++) {
            sheet.setColumnWidth(index, 14 * 256);
        }
        sheet.setColumnWidth(10, 45 * 256);
    }
}
