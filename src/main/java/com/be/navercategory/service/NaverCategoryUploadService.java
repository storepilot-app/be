package com.be.navercategory.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverCategoryUploadService {
    private static final String HEADER_CATEGORY_CODE = "카테고리코드";
    private static final String HEADER_LEVEL1 = "1차카테";
    private static final String HEADER_LEVEL2 = "2차카테";
    private static final String HEADER_LEVEL3 = "3차카테";
    private static final String HEADER_LEVEL4 = "4차카테";
    private static final int VERSION_DIRECTORY_RETENTION_COUNT = 5;

    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;
    private final NaverCategoryEmbeddingService naverCategoryEmbeddingService;

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public NaverCategoryVersion upload(MultipartFile file, boolean skipEmbeddingRebuild) {
        validateFile(file);
        String filename = safeFilename(file.getOriginalFilename());
        String versionTimestamp = String.valueOf(System.currentTimeMillis());
        Path versionDir = uploadRoot()
                .resolve("naver-categories")
                .resolve("versions")
                .resolve(versionTimestamp);
        Path uploadedFilePath = versionDir
                .resolve(filename)
                .normalize();
        Path csvFilePath = uploadRoot()
                .resolve("naver-categories")
                .resolve("active")
                .resolve("naver_categories.csv")
                .normalize();

        try {
            Files.createDirectories(versionDir);
            Files.createDirectories(csvFilePath.getParent());
            file.transferTo(uploadedFilePath);

            NaverCategoryParseResult parseResult = parseCategories(uploadedFilePath);
            List<NaverCategory> categories = parseResult.categories();
            if (categories.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일에 유효한 카테고리 행이 없습니다.");
            }

            log.info(
                    "네이버 카테고리 파일 해석 완료: 전체 행={}, 유효 카테고리={}, 잘못된 행={}, 중복 코드={}",
                    parseResult.sourceRowCount(),
                    categories.size(),
                    parseResult.invalidRowCount(),
                    parseResult.duplicateRowCount()
            );

            naverCategoryVersionRepository.deactivateActiveVersions();
            NaverCategoryVersion version = naverCategoryVersionRepository.save(NaverCategoryVersion.createActive(
                    filename,
                    parseResult.sourceRowCount(),
                    categories.size(),
                    uploadedFilePath.toString(),
                    csvFilePath.toString(),
                    Instant.now()
            ));

            for (NaverCategory category : categories) {
                category.assignVersionId(version.getId());
            }
            naverCategoryRepository.saveAll(categories);
            writeCsv(csvFilePath, categories);
            if (skipEmbeddingRebuild) {
                log.info("네이버 카테고리 임베딩 재생성을 건너뜁니다: versionId={}", version.getId());
            } else {
                naverCategoryEmbeddingService.rebuildEmbeddings(version.getId(), categories);
            }
            cleanupOldVersionDirectories(versionDir.getParent());
            return version;
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_NAVER_CATEGORY_FILE,
                    "네이버 카테고리 파일을 업로드하지 못했습니다."
            );
        }
    }

    private NaverCategoryParseResult parseCategories(Path filePath) {
        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일의 헤더 행이 비어 있습니다.");
            }

            Map<String, Integer> headerIndexes = resolveHeaderIndexes(headerRow);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, NaverCategory> categoriesByCode = new LinkedHashMap<>();
            int sourceRowCount = 0;
            int invalidRowCount = 0;
            int duplicateRowCount = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                sourceRowCount++;

                String categoryCode = readCell(row, headerIndexes.get(HEADER_CATEGORY_CODE), formatter);
                String level1 = readCell(row, headerIndexes.get(HEADER_LEVEL1), formatter);
                String level2 = readCell(row, headerIndexes.get(HEADER_LEVEL2), formatter);
                String level3 = readCell(row, headerIndexes.get(HEADER_LEVEL3), formatter);
                String level4 = readCell(row, headerIndexes.get(HEADER_LEVEL4), formatter);

                if (categoryCode.isBlank() || level1.isBlank()) {
                    invalidRowCount++;
                    continue;
                }

                NaverCategory category = NaverCategory.create(categoryCode, level1, level2, level3, level4);
                if (categoriesByCode.put(category.getCategoryCode(), category) != null) {
                    duplicateRowCount++;
                }
            }

            return new NaverCategoryParseResult(
                    sourceRowCount,
                    invalidRowCount,
                    duplicateRowCount,
                    new ArrayList<>(categoriesByCode.values())
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일을 해석하지 못했습니다.");
        }
    }

    private Map<String, Integer> resolveHeaderIndexes(Row headerRow) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            indexes.put(header, cell.getColumnIndex());
        }

        requireHeader(indexes, HEADER_CATEGORY_CODE);
        requireHeader(indexes, HEADER_LEVEL1);
        requireHeader(indexes, HEADER_LEVEL2);
        requireHeader(indexes, HEADER_LEVEL3);
        requireHeader(indexes, HEADER_LEVEL4);
        return indexes;
    }

    private void requireHeader(Map<String, Integer> indexes, String header) {
        if (!indexes.containsKey(header)) {
            throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "필수 헤더가 없습니다: " + header);
        }
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private void writeCsv(Path csvFilePath, List<NaverCategory> categories) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvFilePath, StandardCharsets.UTF_8))) {
            writer.println("category_code,level1,level2,level3,level4,full_path,search_text");
            for (NaverCategory category : categories) {
                writer.println(String.join(",",
                        csv(category.getCategoryCode()),
                        csv(category.getLevel1()),
                        csv(category.getLevel2()),
                        csv(category.getLevel3()),
                        csv(category.getLevel4()),
                        csv(category.getFullPath()),
                        csv(category.getSearchText())
                ));
            }
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 엑셀 파일을 업로드해 주세요.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !isExcelFilename(filename)) {
            throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 엑셀 파일 형식이 올바르지 않습니다.");
        }
    }

    private boolean isExcelFilename(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "naver_categories.xlsx";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private void cleanupOldVersionDirectories(Path versionsRoot) {
        if (versionsRoot == null || !Files.isDirectory(versionsRoot)) {
            return;
        }
        try (var paths = Files.list(versionsRoot)) {
            paths
                    .filter(Files::isDirectory)
                    .sorted((left, right) -> right.getFileName().toString().compareTo(left.getFileName().toString()))
                    .skip(VERSION_DIRECTORY_RETENTION_COUNT)
                    .forEach(this::deleteDirectoryQuietly);
        } catch (IOException exception) {
            log.warn("이전 네이버 카테고리 업로드 파일 정리에 실패했습니다.", exception);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            log.warn("네이버 카테고리 업로드 파일을 삭제하지 못했습니다: {}", path, exception);
                        }
                    });
        } catch (IOException exception) {
            log.warn("네이버 카테고리 업로드 디렉터리를 탐색하지 못했습니다: {}", directory, exception);
        }
    }

    private record NaverCategoryParseResult(
            int sourceRowCount,
            int invalidRowCount,
            int duplicateRowCount,
            List<NaverCategory> categories
    ) {
    }
}
