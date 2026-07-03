package com.be.keywordjob.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.be.keywordjob.keyword.KeywordCandidateRanker.ScoredKeyword;
import com.be.keywordjob.keyword.KeywordDetailEntry;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class KeywordDetailSheetWriterTest {
    private final KeywordDetailSheetWriter writer = new KeywordDetailSheetWriter();

    @Test
    void writesScoresAndReasonsToASeparateSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("상품");
            writer.write(workbook, List.of(new KeywordDetailEntry(
                    2,
                    "로지텍 K380 블루투스 키보드",
                    "디지털/가전 > 키보드 > 무선키보드",
                    1,
                    new ScoredKeyword("블루투스키보드", 0.91, 1.0, 0.7, 1.0, 1.0, 0),
                    List.of("유사상품 반복 표현", "상품명 + 카테고리 조합")
            )));

            Sheet detail = workbook.getSheet(KeywordDetailSheetWriter.SHEET_NAME);
            assertNotNull(detail);
            assertEquals("키워드", detail.getRow(0).getCell(3).getStringCellValue());
            assertEquals("블루투스키보드", detail.getRow(1).getCell(3).getStringCellValue());
            assertEquals(CellType.NUMERIC, detail.getRow(1).getCell(5).getCellType());
            assertEquals(0.91, detail.getRow(1).getCell(5).getNumericCellValue());
            assertEquals(
                    "유사상품 반복 표현, 상품명 + 카테고리 조합",
                    detail.getRow(1).getCell(10).getStringCellValue()
            );
        }
    }

    @Test
    void replacesAnExistingDetailSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet(KeywordDetailSheetWriter.SHEET_NAME).createRow(0).createCell(0).setCellValue("old");

            writer.write(workbook, List.of());

            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("원본 행", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }
}
