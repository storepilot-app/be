package com.be.trainingproductrequest.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.trainingproductrequest.domain.TrainingProductRequest;
import com.be.trainingproductrequest.domain.TrainingProductRequestStatus;
import com.be.trainingproductrequest.dto.TrainingProductRequestFile;
import com.be.trainingproductrequest.repository.TrainingProductRequestRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingProductRequestService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_PRODUCT_COUNT = 50_000;
    private static final List<String> PRODUCT_NAME_HEADERS = List.of("상품명");
    private static final List<String> MY_CATEGORY_HEADERS = List.of("마이카테", "마이카테고리", "마이카테고리코드");

    private final TrainingProductRequestRepository trainingProductRequestRepository;

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public TrainingProductRequest submit(Long userId, String userEmail, MultipartFile file) {
        validateUser(userId, userEmail);
        validateFile(file);

        byte[] content = readFile(file);
        int productCount = validateAndCountProducts(content);
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + ".xlsx";
        Path targetPath = requestDirectory().resolve(storedFilename);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
            return trainingProductRequestRepository.save(TrainingProductRequest.create(
                    userId,
                    userEmail,
                    originalFilename,
                    storedFilename,
                    content.length,
                    productCount
            ));
        } catch (IOException error) {
            throw invalid("학습 요청 파일을 저장하지 못했습니다.");
        } catch (RuntimeException error) {
            deleteQuietly(targetPath);
            throw error;
        }
    }

    public List<TrainingProductRequest> getMyRequests(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return trainingProductRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<TrainingProductRequest> getAllRequests() {
        return trainingProductRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public TrainingProductRequestFile getRequestFile(Long requestId) {
        TrainingProductRequest request = getRequest(requestId);
        if (request.getFileDeletedAt() != null) {
            throw invalid("이미 삭제된 학습 요청 파일입니다.");
        }
        try {
            byte[] content = Files.readAllBytes(requestDirectory().resolve(request.getStoredFilename()));
            return new TrainingProductRequestFile(request.getOriginalFilename(), content);
        } catch (IOException error) {
            throw invalid("학습 요청 파일을 읽지 못했습니다.");
        }
    }

    @Transactional
    public TrainingProductRequest updateStatus(Long requestId, TrainingProductRequestStatus status) {
        if (status == null) {
            throw invalid("변경할 학습 요청 상태가 필요합니다.");
        }
        TrainingProductRequest request = getRequest(requestId);
        if (request.getFileDeletedAt() != null && status != TrainingProductRequestStatus.COMPLETED) {
            throw invalid("원본 파일이 삭제된 요청은 학습 완료 상태를 변경할 수 없습니다.");
        }
        request.updateStatus(status);
        return request;
    }

    @Transactional
    public TrainingProductRequest deleteRequestFile(Long requestId) {
        TrainingProductRequest request = getRequest(requestId);
        Path filePath = requestDirectory().resolve(request.getStoredFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException error) {
            throw invalid("학습 요청 파일을 삭제하지 못했습니다.");
        }
        request.markFileDeleted();
        return request;
    }

    private TrainingProductRequest getRequest(Long requestId) {
        return trainingProductRequestRepository.findById(requestId)
                .orElseThrow(() -> invalid("학습 요청을 찾을 수 없습니다."));
    }

    private void validateUser(Long userId, String userEmail) {
        if (userId == null || userEmail == null || userEmail.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw invalid("기존 상품 엑셀 파일을 선택해 주세요.");
        }
        if (!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalid("기존 상품 파일은 .xlsx 형식이어야 합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalid("기존 상품 파일은 20MB 이하만 업로드할 수 있습니다.");
        }
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw invalid("기존 상품 엑셀 파일을 읽지 못했습니다.");
        }
    }

    private int validateAndCountProducts(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalid("기존 상품 엑셀 파일에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw invalid("기존 상품 엑셀 파일의 1행이 비어 있습니다.");
            }

            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            int productNameColumn = findRequiredColumn(headerRow, PRODUCT_NAME_HEADERS, "상품명", formatter);
            int myCategoryColumn = findRequiredColumn(headerRow, MY_CATEGORY_HEADERS, "마이카테", formatter);
            int productCount = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String productName = formatter.formatCellValue(row.getCell(productNameColumn)).trim();
                String myCategory = formatter.formatCellValue(row.getCell(myCategoryColumn)).trim();
                if (!productName.isBlank() && !myCategory.isBlank()) {
                    productCount++;
                    if (productCount > MAX_PRODUCT_COUNT) {
                        throw invalid("한 번에 요청할 수 있는 상품은 최대 50,000개입니다.");
                    }
                }
            }
            if (productCount == 0) {
                throw invalid("상품명과 마이카테가 입력된 상품이 없습니다.");
            }
            return productCount;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("올바른 .xlsx 파일인지 확인해 주세요.");
        }
    }

    private int findRequiredColumn(
            Row headerRow,
            List<String> acceptedHeaders,
            String displayName,
            DataFormatter formatter
    ) {
        for (var cell : headerRow) {
            String value = normalizeHeader(formatter.formatCellValue(cell));
            if (acceptedHeaders.stream().map(this::normalizeHeader).anyMatch(value::equals)) {
                return cell.getColumnIndex();
            }
        }
        throw invalid("1행에서 '" + displayName + "' 열을 찾을 수 없습니다.");
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String safeOriginalFilename(String filename) {
        String safeFilename = filename.replace('\\', '/');
        safeFilename = safeFilename.substring(safeFilename.lastIndexOf('/') + 1).trim();
        if (safeFilename.length() > 255) {
            throw invalid("파일 이름은 255자 이하여야 합니다.");
        }
        return safeFilename;
    }

    private Path requestDirectory() {
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("training-product-requests");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TRAINING_PRODUCT_FILE, message);
    }
}
