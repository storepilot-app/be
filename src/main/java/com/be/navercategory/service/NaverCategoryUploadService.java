package com.be.navercategory.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.categorymatcher.service.CategoryMatcherService;
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
public class NaverCategoryUploadService {
    private static final String HEADER_CATEGORY_CODE = "카테고리코드";
    private static final String HEADER_LEVEL1 = "1차카테";
    private static final String HEADER_LEVEL2 = "2차카테";
    private static final String HEADER_LEVEL3 = "3차카테";
    private static final String HEADER_LEVEL4 = "4차카테";

    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;
    private final CategoryMatcherService categoryMatcherService;

    @Value("${storepilot.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public NaverCategoryVersion upload(MultipartFile file) {
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

            List<NaverCategory> categories = parseCategories(uploadedFilePath);
            if (categories.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일에 유효한 카테고리 행이 없습니다.");
            }

            naverCategoryVersionRepository.deactivateActiveVersions();
            NaverCategoryVersion version = naverCategoryVersionRepository.save(NaverCategoryVersion.createActive(
                    filename,
                    categories.size(),
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
            categoryMatcherService.rebuildEmbeddings(version.getId());
            return version;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일을 업로드하지 못했습니다.");
        }
    }

    private List<NaverCategory> parseCategories(Path filePath) {
        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_NAVER_CATEGORY_FILE, "네이버 카테고리 파일의 헤더 행이 비어 있습니다.");
            }

            Map<String, Integer> headerIndexes = resolveHeaderIndexes(headerRow);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, NaverCategory> categoriesByCode = new LinkedHashMap<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String categoryCode = readCell(row, headerIndexes.get(HEADER_CATEGORY_CODE), formatter);
                String level1 = readCell(row, headerIndexes.get(HEADER_LEVEL1), formatter);
                String level2 = readCell(row, headerIndexes.get(HEADER_LEVEL2), formatter);
                String level3 = readCell(row, headerIndexes.get(HEADER_LEVEL3), formatter);
                String level4 = readCell(row, headerIndexes.get(HEADER_LEVEL4), formatter);

                if (categoryCode.isBlank() || level1.isBlank()) {
                    continue;
                }

                NaverCategory category = createCategory(categoryCode, level1, level2, level3, level4);
                categoriesByCode.put(category.getCategoryCode(), category);
            }

            return new ArrayList<>(categoriesByCode.values());
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

    private NaverCategory createCategory(String categoryCode, String level1, String level2, String level3, String level4) {
        List<String> levels = List.of(level1, level2, level3, level4).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        String fullPath = String.join(" > ", levels);
        String searchText = String.join(" ", levels);
        return new NaverCategory(null, categoryCode, level1, level2, level3, level4, fullPath, searchText);
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
}
