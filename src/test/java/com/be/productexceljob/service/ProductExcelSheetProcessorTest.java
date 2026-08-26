package com.be.productexceljob.service;

import static com.be.productexceljob.excel.ProductExcelLayout.KEYWORD_COLUMN_INDEX;
import static com.be.productexceljob.excel.ProductExcelLayout.KEYWORD_HEADER;
import static com.be.productexceljob.excel.ProductExcelLayout.MY_CATEGORY_COLUMN_INDEX;
import static com.be.productexceljob.excel.ProductExcelLayout.MY_CATEGORY_HEADER;
import static com.be.productexceljob.excel.ProductExcelLayout.NAVER_CATEGORY_COLUMN_INDEX;
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
    private final ProductExcelSheetProcessor processor =
            new ProductExcelSheetProcessor(new KeywordDetailSheetWriter());

    @Test
    void preparesResultColumnsAndReadsOnlyRowsWithProductNames() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("상품");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("상품명");
            header.createCell(1).setCellValue("카테고리");

            Row productRow = sheet.createRow(1);
            productRow.createCell(0).setCellValue("무선 마우스");
            productRow.createCell(1).setCellValue("디지털/가전");
            sheet.createRow(2).createCell(1).setCellValue("상품명 없음");

            ProductExcelSheetProcessor.ProductExcelSheetContext context = processor.prepareSheet(
                    workbook,
                    "상품명",
                    "카테고리",
                    false
            );
            List<ProductExcelSheetProcessor.ProductExcelRow> rows = processor.readProductRows(context);

            assertEquals(KEYWORD_HEADER, header.getCell(KEYWORD_COLUMN_INDEX).getStringCellValue());
            assertEquals(MY_CATEGORY_HEADER, header.getCell(MY_CATEGORY_COLUMN_INDEX).getStringCellValue());
            assertEquals(NAVER_CATEGORY_HEADER, header.getCell(NAVER_CATEGORY_COLUMN_INDEX).getStringCellValue());
            assertEquals(1, rows.size());
            assertEquals(1, rows.getFirst().rowId());
            assertEquals("무선 마우스", rows.getFirst().productName());
            assertEquals("디지털/가전", rows.getFirst().category());
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
