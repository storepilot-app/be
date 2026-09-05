package com.be.mycategory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.be.global.exception.BusinessException;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.mycategory.repository.MyCategoryMappingVersionRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MyCategoryMappingUploadServiceTest {
    private MyCategoryMappingRepository mappingRepository;
    private MyCategoryMappingUploadService service;
    private AtomicReference<List<MyCategoryMapping>> savedMappings;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(MyCategoryMappingRepository.class);
        MyCategoryMappingVersionRepository versionRepository = mock(MyCategoryMappingVersionRepository.class);
        NaverCategoryRepository naverCategoryRepository = mock(NaverCategoryRepository.class);
        NaverCategoryVersionRepository naverCategoryVersionRepository = mock(NaverCategoryVersionRepository.class);

        NaverCategoryVersion activeVersion = mock(NaverCategoryVersion.class);
        when(activeVersion.getId()).thenReturn(10L);
        when(naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc())
                .thenReturn(Optional.of(activeVersion));

        NaverCategory naverCategory = mock(NaverCategory.class);
        when(naverCategory.getId()).thenReturn(20L);
        when(naverCategory.getCategoryCode()).thenReturn("50000167");
        when(naverCategory.getFullPath()).thenReturn("패션의류 > 여성의류");
        when(naverCategoryRepository.findByVersionId(10L)).thenReturn(List.of(naverCategory));

        MyCategoryMappingVersion savedVersion = mock(MyCategoryMappingVersion.class);
        when(savedVersion.getId()).thenReturn(30L);
        when(versionRepository.save(any(MyCategoryMappingVersion.class))).thenReturn(savedVersion);

        savedMappings = new AtomicReference<>(List.of());
        when(mappingRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MyCategoryMapping> iterable = invocation.getArgument(0);
            List<MyCategoryMapping> values = new ArrayList<>();
            iterable.forEach(values::add);
            savedMappings.set(values);
            return values;
        });

        service = new MyCategoryMappingUploadService(
                mappingRepository,
                versionRepository,
                naverCategoryRepository,
                naverCategoryVersionRepository
        );
    }

    @Test
    void findsMappingColumnsByFirstRowHeadersRegardlessOfColumnOrder() throws IOException {
        MockMultipartFile file = excelFile(
                List.of("상품명", "네이버카테", "비고", "마이카테"),
                List.of("원피스", "50000167", "", "MY-001")
        );

        service.upload(file, 1L);

        assertEquals(1, savedMappings.get().size());
        MyCategoryMapping mapping = savedMappings.get().getFirst();
        assertEquals("MY-001", mapping.getMyCategoryCode());
        assertEquals("50000167", mapping.getNaverCategoryValue());
        assertEquals("50000167", mapping.getNaverCategoryCode());
        assertEquals("패션의류 > 여성의류", mapping.getNaverCategoryFullPath());
    }

    @Test
    void readsResolvedMappingsForTrainingWithoutSavingThem() throws IOException {
        MockMultipartFile file = excelFile(
                List.of("네이버카테", "마이카테"),
                List.of("50000167", "MY-001")
        );

        List<MyCategoryMapping> mappings = service.readResolvedMappings(file, 1L);

        assertEquals(1, mappings.size());
        assertEquals("MY-001", mappings.getFirst().getMyCategoryCode());
        assertEquals("50000167", mappings.getFirst().getNaverCategoryCode());
        assertEquals(0, savedMappings.get().size());
    }

    @Test
    void rejectsExcelWhenRequiredHeaderIsMissing() throws IOException {
        MockMultipartFile file = excelFile(
                List.of("마이카테", "다른열"),
                List.of("MY-001", "50000167")
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(file, 1L));

        assertEquals("첫 번째 행에 '마이카테'와 '네이버카테' 열이 모두 있어야 합니다.", exception.getMessage());
    }

    private MockMultipartFile excelFile(List<String> headers, List<String> values) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("매핑");
            Row headerRow = sheet.createRow(0);
            Row valueRow = sheet.createRow(1);
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
                valueRow.createCell(index).setCellValue(values.get(index));
            }
            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "my-category.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }
}
