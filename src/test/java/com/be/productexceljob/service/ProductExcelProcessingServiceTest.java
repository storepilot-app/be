package com.be.productexceljob.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.be.categorymatcher.dto.MyCategoryMatchResult;
import com.be.categorymatcher.service.CategoryMatcherService;
import com.be.productexceljob.domain.ProductExcelJob;
import com.be.productexceljob.dto.ProductExcelProcessingResult;
import com.be.productexceljob.excel.KeywordDetailSheetWriter;
import com.be.productexceljob.repository.ProductExcelJobRepository;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductExcelProcessingServiceTest {
    @TempDir
    Path directory;

    @Test
    void createsUserResultWithoutSelectionDetailsAndAdminResultWithThem() throws Exception {
        Path input = directory.resolve("input.xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(input)) {
            var sheet = workbook.createSheet("상품");
            sheet.createRow(0).createCell(0).setCellValue("상품명");
            sheet.createRow(1).createCell(0).setCellValue("테스트 상품");
            workbook.write(output);
        }

        CategoryMatcherService categoryMatcherService = mock(CategoryMatcherService.class);
        when(categoryMatcherService.findCategoryMatches(any(), eq(1L), any()))
                .thenReturn(Map.of(1, MyCategoryMatchResult.matched("MY1", "생활/건강", List.of())));
        ProductKeywordGenerator keywordGenerator = mock(ProductKeywordGenerator.class);
        when(keywordGenerator.generate(any(), eq(30))).thenReturn(Map.of());
        ProductExcelProcessingService service = new ProductExcelProcessingService(
                categoryMatcherService,
                new ProductExcelSheetProcessor(new KeywordDetailSheetWriter()),
                keywordGenerator
        );
        ProductExcelJob job = ProductExcelJob.register(
                1L,
                "input.xlsx",
                input,
                false,
                1,
                LocalDate.of(2026, 9, 28)
        );
        ProductExcelProcessingResult result = service.processExcel(
                new ProductExcelProcessingRequest(input, "input.xlsx", "상품명", "", 30, 1L, false),
                new ProductExcelJobProgressUpdater(job, mock(ProductExcelJobRepository.class))
        );

        assertThat(hasHeader(result.userResult().content(), "선택카테고리")).isFalse();
        assertThat(hasHeader(result.adminResult().content(), "선택카테고리")).isTrue();
    }

    private boolean hasHeader(byte[] content, String expectedHeader) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            for (var cell : header) {
                if (expectedHeader.equals(formatter.formatCellValue(cell))) {
                    return true;
                }
            }
            return false;
        }
    }
}
