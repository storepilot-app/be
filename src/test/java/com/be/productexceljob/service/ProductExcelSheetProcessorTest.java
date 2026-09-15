package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.KEYWORD_HEADER;
import static com.be.productexceljob.excel.ProductExcelLayout.MY_CATEGORY_HEADER;
import static com.be.productexceljob.excel.ProductExcelLayout.NAVER_CATEGORY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.be.global.exception.BusinessException;
import com.be.productexceljob.excel.KeywordDetailSheetWriter;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ProductExcelSheetProcessorTest {
    @Test
    void writesReorderedHeadersWithoutOverwritingOtherColumns() throws Exception {
        for (boolean details : new boolean[]{false, true}) {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet();
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("상품명");
                header.createCell(30).setCellValue("네이버 카테");
                header.createCell(3).setCellValue("마이카테");
                header.createCell(7).setCellValue("키워드");
                Row row = sheet.createRow(1);
                row.createCell(0).setCellValue("상품");
                for (int index : new int[]{11, 19, 20, 26, 40}) row.createCell(index).setCellValue("보존");
                var context = processor.prepareSheet(workbook, "상품명", "", details);
                var keyword = new ProductKeywordGenerator.GeneratedKeyword(
                        new com.be.keyword.KeywordCandidateRanker.ScoredKeyword("테스트 키워드", 1, 1, 1, 1, 1, 0), List.of());
                processor.writeProductResultRows(processor.readProductRows(context),
                        java.util.Map.of(1, com.be.categorymatcher.dto.MyCategoryMatchResult.matched("MY1", "분류", List.of())),
                        java.util.Map.of(), java.util.Map.of(1, List.of(keyword)), context, details);
                assertEquals("MY1", row.getCell(3).getStringCellValue());
                assertEquals("테스트키워드", row.getCell(7).getStringCellValue());
                assertEquals("분류", row.getCell(30).getStringCellValue());
                for (int index : new int[]{11, 19, 20, 26, 40}) assertEquals("보존", row.getCell(index).getStringCellValue());
                org.junit.jupiter.api.Assertions.assertFalse(sheet.isColumnHidden(30));
            }
        }
    }

    @Test
    void appendsMissingHeadersAfterHeaderlessDataAndRejectsDuplicates() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("상품명");
            sheet.createRow(1).createCell(50).setCellValue("헤더 없는 데이터");
            var context = processor.prepareSheet(workbook, "상품명", "", false);
            assertEquals(51, context.keywordColumnIndex());
            assertEquals(52, context.myCategoryColumnIndex());
            assertEquals(53, context.naverCategoryColumnIndex());
            header.createCell(54).setCellValue("네이버 카테");
            assertThrows(BusinessException.class, () -> processor.prepareSheet(workbook, "상품명", "", false));
        }
    }
    private final ProductExcelSheetProcessor processor =
            new ProductExcelSheetProcessor(new KeywordDetailSheetWriter());

    @Test
    void preparesResultColumnsAndReadsOnlyRowsWithProductNames() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("상품");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("상품명");
            header.createCell(1).setCellValue("카테고리");
            header.createCell(5).setCellValue("목록이미지1");

            Row productRow = sheet.createRow(1);
            productRow.createCell(0).setCellValue("무선 마우스");
            productRow.createCell(1).setCellValue("디지털/가전");
            productRow.createCell(5).setCellValue("https://example.com/mouse.jpg");
            sheet.createRow(2).createCell(1).setCellValue("상품명 없음");

            ProductExcelSheetProcessor.ProductExcelSheetContext context = processor.prepareSheet(
                    workbook,
                    "상품명",
                    "카테고리",
                    false
            );
            List<ProductExcelSheetProcessor.ProductExcelRow> rows = processor.readProductRows(context);

            assertEquals(KEYWORD_HEADER, header.getCell(context.keywordColumnIndex()).getStringCellValue());
            assertEquals(MY_CATEGORY_HEADER, header.getCell(context.myCategoryColumnIndex()).getStringCellValue());
            assertEquals(NAVER_CATEGORY_HEADER, header.getCell(context.naverCategoryColumnIndex()).getStringCellValue());
            assertEquals(1, rows.size());
            assertEquals(1, rows.getFirst().rowId());
            assertEquals("무선 마우스", rows.getFirst().productName());
            assertEquals("디지털/가전", rows.getFirst().category());
            assertEquals("https://example.com/mouse.jpg", rows.getFirst().imageUrl());
        }
    }

    @Test
    void rejectsWorkbookWithoutProductNameColumn() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("상품").createRow(0).createCell(0).setCellValue("다른 열");

            assertThrows(
                    BusinessException.class,
                    () -> processor.prepareSheet(workbook, "상품명", "카테고리", false)
            );
        }
    }
}
