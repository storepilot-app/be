package com.be.mycategory.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.mycategory.repository.MyCategoryMappingVersionRepository;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MyCategoryMappingUploadService {
    private static final String MY_CATEGORY_HEADER = "마이카테";
    private static final String NAVER_CATEGORY_HEADER = "네이버카테";

    private final MyCategoryMappingRepository myCategoryMappingRepository;
    private final MyCategoryMappingVersionRepository myCategoryMappingVersionRepository;
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;

    public List<MyCategoryMapping> readResolvedMappings(MultipartFile file, Long userId) {
        validateFile(file);
        validateUserId(userId);
        Map<String, NaverCategory> naverCategoriesByCode = loadRequiredActiveNaverCategoriesByCode();
        return parseMappings(file, userId, naverCategoriesByCode)
                .mappings()
                .stream()
                .filter(mapping -> mapping.getNaverCategoryId() != null)
                .toList();
    }

    @Transactional
    public MyCategoryMappingVersion upload(MultipartFile file, Long userId) {
        validateFile(file);
        validateUserId(userId);
        String filename = safeFilename(file.getOriginalFilename());
        Map<String, NaverCategory> naverCategoriesByCode = loadRequiredActiveNaverCategoriesByCode();

        MyCategoryMappingParseResult parseResult = parseMappings(
                file,
                userId,
                naverCategoriesByCode
        );
        List<MyCategoryMapping> mappings = parseResult.mappings();
        if (mappings.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 파일에 유효한 매핑 행이 없습니다.");
        }

        logParseResult(parseResult);

        replaceExistingMappings(userId);
        MyCategoryMappingVersion version = myCategoryMappingVersionRepository.save(MyCategoryMappingVersion.createActive(
                userId,
                filename,
                parseResult.sourceRowCount(),
                mappings.size(),
                parseResult.matchedCount(),
                Instant.now()
        ));

        for (MyCategoryMapping mapping : mappings) {
            mapping.assignVersionId(version.getId());
        }
        myCategoryMappingRepository.saveAll(mappings);
        return version;
    }

    private void replaceExistingMappings(Long userId) {
        myCategoryMappingRepository.deleteByUserId(userId);
        myCategoryMappingVersionRepository.deleteByUserId(userId);
    }

    private void logParseResult(MyCategoryMappingParseResult parseResult) {
        log.info(
                "마이카테고리 매핑 파일 해석 완료: 전체 행={}, 유효 매핑={}, 잘못된 행={}, 중복 코드={}, 네이버 카테고리 일치={}",
                parseResult.sourceRowCount(),
                parseResult.mappings().size(),
                parseResult.invalidRowCount(),
                parseResult.duplicateRowCount(),
                parseResult.matchedCount()
        );
    }

    private MyCategoryMappingParseResult parseMappings(
            MultipartFile file,
            Long userId,
            Map<String, NaverCategory> naverCategoriesByCode
    ) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            HeaderColumns headerColumns = resolveHeaderColumns(sheet.getRow(0), formatter);
            Map<String, MyCategoryMapping> mappingsByMyCategory = new LinkedHashMap<>();
            int sourceRowCount = 0;
            int invalidRowCount = 0;
            int duplicateRowCount = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                sourceRowCount++;

                String myCategoryCode = readCell(row, headerColumns.myCategoryColumnIndex(), formatter);
                String naverCategoryCode = readCell(row, headerColumns.naverCategoryColumnIndex(), formatter);
                validateNaverCategoryCode(myCategoryCode, naverCategoryCode, rowIndex + 1);
                if (myCategoryCode.isBlank() || naverCategoryCode.isBlank()) {
                    invalidRowCount++;
                    continue;
                }

                NaverCategory naverCategory = naverCategoriesByCode.get(naverCategoryCode);

                MyCategoryMapping mapping = MyCategoryMapping.create(
                        userId,
                        myCategoryCode,
                        naverCategoryCode,
                        naverCategory == null ? null : naverCategory.getId(),
                        naverCategory == null ? null : naverCategory.getCategoryCode(),
                        naverCategory == null ? null : naverCategory.getFullPath()
                );
                if (mappingsByMyCategory.put(myCategoryCode, mapping) != null) {
                    duplicateRowCount++;
                }
            }

            List<MyCategoryMapping> mappings = new ArrayList<>(mappingsByMyCategory.values());
            int matchedCount = (int) mappings.stream()
                    .filter(mapping -> mapping.getNaverCategoryId() != null)
                    .count();
            return new MyCategoryMappingParseResult(
                    sourceRowCount,
                    invalidRowCount,
                    duplicateRowCount,
                    matchedCount,
                    mappings
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 파일을 해석하지 못했습니다.");
        }
    }

    private Map<String, NaverCategory> loadRequiredActiveNaverCategoriesByCode() {
        NaverCategoryVersion activeVersion = naverCategoryVersionRepository
                .findFirstByActiveTrueOrderByUploadedAtDesc()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE,
                        "활성화된 네이버 카테고리 버전이 없습니다. 네이버 카테고리를 먼저 업로드해 주세요."
                ));

        Map<String, NaverCategory> categoriesByCode = new LinkedHashMap<>();
        naverCategoryRepository.findByVersionId(activeVersion.getId())
                .forEach(category -> categoriesByCode.putIfAbsent(category.getCategoryCode(), category));
        if (categoriesByCode.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE,
                    "활성화된 네이버 카테고리 버전에 카테고리 데이터가 없습니다."
            );
        }
        return categoriesByCode;
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private HeaderColumns resolveHeaderColumns(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw invalidHeader("첫 번째 행에 열 제목이 없습니다.");
        }

        Integer myCategoryColumnIndex = null;
        Integer naverCategoryColumnIndex = null;
        for (int columnIndex = 0; columnIndex < headerRow.getLastCellNum(); columnIndex++) {
            String header = readCell(headerRow, columnIndex, formatter);
            if (MY_CATEGORY_HEADER.equals(header)) {
                if (myCategoryColumnIndex != null) {
                    throw invalidHeader("'" + MY_CATEGORY_HEADER + "' 열이 두 개 이상 있습니다.");
                }
                myCategoryColumnIndex = columnIndex;
            }
            if (NAVER_CATEGORY_HEADER.equals(header)) {
                if (naverCategoryColumnIndex != null) {
                    throw invalidHeader("'" + NAVER_CATEGORY_HEADER + "' 열이 두 개 이상 있습니다.");
                }
                naverCategoryColumnIndex = columnIndex;
            }
        }

        if (myCategoryColumnIndex == null || naverCategoryColumnIndex == null) {
            throw invalidHeader("첫 번째 행에 '마이카테'와 '네이버카테' 열이 모두 있어야 합니다.");
        }
        return new HeaderColumns(myCategoryColumnIndex, naverCategoryColumnIndex);
    }

    private void validateNaverCategoryCode(
            String myCategoryCode,
            String naverCategoryCode,
            int rowNumber
    ) {
        if (!myCategoryCode.isBlank() && naverCategoryCode.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE,
                    "'네이버카테' 열에 네이버 카테고리 코드가 없습니다. 엑셀 행: " + rowNumber
            );
        }
    }

    private BusinessException invalidHeader(String message) {
        return new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, message);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 엑셀 파일을 업로드해 주세요.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !isExcelFilename(filename)) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 엑셀 파일 형식이 올바르지 않습니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "로그인이 필요합니다.");
        }
    }

    private boolean isExcelFilename(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "my_category_mappings.xlsx";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record MyCategoryMappingParseResult(
            int sourceRowCount,
            int invalidRowCount,
            int duplicateRowCount,
            int matchedCount,
            List<MyCategoryMapping> mappings
    ) {
    }

    private record HeaderColumns(
            int myCategoryColumnIndex,
            int naverCategoryColumnIndex
    ) {
    }
}
