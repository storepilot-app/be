package com.be.trainingproductrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.be.global.exception.BusinessException;
import com.be.trainingproductrequest.domain.TrainingProductRequest;
import com.be.trainingproductrequest.domain.TrainingProductRequestStatus;
import com.be.trainingproductrequest.repository.TrainingProductRequestRepository;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingProductRequestServiceTest {
    private TrainingProductRequestService service;
    private TrainingProductRequestRepository repository;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void setUp() {
        repository = mock(TrainingProductRequestRepository.class);
        when(repository.save(any(TrainingProductRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TrainingProductRequestService(repository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
    }

    @Test
    void submitsValidExistingProductFile() throws Exception {
        MockMultipartFile file = excelFile("상품명", "마이카테", "린넨 셔츠", "A001");

        TrainingProductRequest request = service.submit(1L, "user@example.com", file);

        assertThat(request.getOriginalFilename()).isEqualTo("products.xlsx");
        assertThat(request.getProductCount()).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(TrainingProductRequestStatus.RECEIVED);
        assertThat(tempDirectory.resolve("training-product-requests").resolve(request.getStoredFilename())).exists();
    }

    @Test
    void updatesRequestStatus() throws Exception {
        TrainingProductRequest request = service.submit(
                1L,
                "user@example.com",
                excelFile("상품명", "마이카테", "린넨 셔츠", "A001")
        );
        when(repository.findById(10L)).thenReturn(Optional.of(request));

        TrainingProductRequest updated = service.updateStatus(10L, TrainingProductRequestStatus.REVIEWING);

        assertThat(updated.getStatus()).isEqualTo(TrainingProductRequestStatus.REVIEWING);
    }

    @Test
    void deletesStoredFileAndKeepsCompletedRequest() throws Exception {
        TrainingProductRequest request = service.submit(
                1L,
                "user@example.com",
                excelFile("상품명", "마이카테", "린넨 셔츠", "A001")
        );
        Path storedFile = tempDirectory.resolve("training-product-requests").resolve(request.getStoredFilename());
        when(repository.findById(10L)).thenReturn(Optional.of(request));

        TrainingProductRequest updated = service.deleteRequestFile(10L);

        assertThat(storedFile).doesNotExist();
        assertThat(updated.getStatus()).isEqualTo(TrainingProductRequestStatus.COMPLETED);
        assertThat(updated.getFileDeletedAt()).isNotNull();
    }

    @Test
    void rejectsFileWithoutRequiredHeader() throws Exception {
        MockMultipartFile file = excelFile("제품", "분류", "린넨 셔츠", "A001");

        assertThatThrownBy(() -> service.submit(1L, "user@example.com", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("상품명");
    }

    private MockMultipartFile excelFile(
            String firstHeader,
            String secondHeader,
            String firstValue,
            String secondValue
    ) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(firstHeader);
            header.createCell(1).setCellValue(secondHeader);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(firstValue);
            row.createCell(1).setCellValue(secondValue);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
