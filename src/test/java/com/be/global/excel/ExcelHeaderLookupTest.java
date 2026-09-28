package com.be.global.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelHeaderLookupTest {
    @Test
    void findsExactWhitespaceIgnoredAndAliasHeaders() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet().createRow(0);
            header.createCell(0).setCellValue("상품명");
            header.createCell(2).setCellValue("마이 카테");
            header.createCell(4).setCellValue("PRODUCT NAME");
            header.createCell(5).setCellValue("상품명");

            ExcelHeaderLookup lookup = ExcelHeaderLookup.from(header);

            assertThat(lookup.findFirst("상품명")).isZero();
            assertThat(lookup.findAll("상품명")).containsExactly(0, 5);
            assertThat(lookup.findFirstIgnoringWhitespace("마이카테")).isEqualTo(2);
            assertThat(lookup.findFirstNormalized(List.of("productname"))).isEqualTo(4);
            assertThat(lookup.findFirst("없는 열")).isEqualTo(-1);
        }
    }
}
