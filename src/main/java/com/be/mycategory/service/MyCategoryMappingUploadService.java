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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
public class MyCategoryMappingUploadService {
    private static final int MY_CATEGORY_COLUMN_INDEX = 0;
    private static final int NAVER_CATEGORY_COLUMN_INDEX = 7;

    private final MyCategoryMappingRepository myCategoryMappingRepository;
    private final MyCategoryMappingVersionRepository myCategoryMappingVersionRepository;
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;

    @Transactional
    public MyCategoryMappingVersion upload(MultipartFile file, String userKey) {
        validateFile(file);
        String normalizedUserKey = validateAndTrimUserKey(userKey);
        String filename = safeFilename(file.getOriginalFilename());

        List<MyCategoryMapping> mappings = parseMappings(file, normalizedUserKey);
        if (mappings.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 파일에 유효한 매핑 행이 없습니다.");
        }

        int matchedCount = (int) mappings.stream()
                .filter(mapping -> mapping.getNaverCategoryId() != null)
                .count();

        replaceExistingMappings(normalizedUserKey);
        MyCategoryMappingVersion version = myCategoryMappingVersionRepository.save(new MyCategoryMappingVersion(
                normalizedUserKey,
                filename,
                mappings.size(),
                mappings.size(),
                matchedCount,
                Instant.now(),
                true
        ));

        for (MyCategoryMapping mapping : mappings) {
            mapping.assignVersionId(version.getId());
        }
        myCategoryMappingRepository.saveAll(mappings);
        return version;
    }

    private void replaceExistingMappings(String userKey) {
        myCategoryMappingRepository.deleteByUserKey(userKey);
        myCategoryMappingVersionRepository.deleteByUserKey(userKey);
    }

    private List<MyCategoryMapping> parseMappings(MultipartFile file, String userKey) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, MyCategoryMapping> mappingsByMyCategory = new LinkedHashMap<>();
            Optional<Long> activeNaverVersionId = naverCategoryVersionRepository.findFirstByActiveTrueOrderByUploadedAtDesc()
                    .map(NaverCategoryVersion::getId);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String myCategoryCode = readCell(row, MY_CATEGORY_COLUMN_INDEX, formatter);
                String naverCategoryValue = readCell(row, NAVER_CATEGORY_COLUMN_INDEX, formatter);
                if (myCategoryCode.isBlank() || naverCategoryValue.isBlank()) {
                    continue;
                }

                Optional<NaverCategory> naverCategory = activeNaverVersionId
                        .flatMap(versionId -> findNaverCategory(versionId, naverCategoryValue));

                MyCategoryMapping mapping = new MyCategoryMapping(
                        null,
                        userKey,
                        myCategoryCode,
                        naverCategoryValue,
                        naverCategory.map(NaverCategory::getId).orElse(null),
                        naverCategory.map(NaverCategory::getCategoryCode).orElse(null),
                        naverCategory.map(NaverCategory::getFullPath).orElse(null)
                );
                mappingsByMyCategory.put(myCategoryCode, mapping);
            }

            return new ArrayList<>(mappingsByMyCategory.values());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "마이카테고리 매핑 파일을 해석하지 못했습니다.");
        }
    }

    private Optional<NaverCategory> findNaverCategory(Long versionId, String naverCategoryValue) {
        return naverCategoryRepository.findFirstByVersionIdAndCategoryCode(versionId, naverCategoryValue)
                .or(() -> naverCategoryRepository.findFirstByVersionIdAndFullPath(versionId, normalizeCategoryPath(naverCategoryValue)));
    }

    private String normalizeCategoryPath(String value) {
        return value.replaceAll("\\s*>\\s*", " > ").trim();
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
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

    private String validateAndTrimUserKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, "사용자 식별자는 필수입니다.");
        }
        return userKey.trim();
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
}
