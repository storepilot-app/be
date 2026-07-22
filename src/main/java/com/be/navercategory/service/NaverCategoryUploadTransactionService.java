package com.be.navercategory.service;

import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import com.be.navercategory.repository.NaverCategoryRepository;
import com.be.navercategory.repository.NaverCategoryVersionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NaverCategoryUploadTransactionService {
    private final NaverCategoryRepository naverCategoryRepository;
    private final NaverCategoryVersionRepository naverCategoryVersionRepository;

    @Transactional
    public NaverCategoryVersion saveNewActiveVersion(
            String filename,
            int sourceRowCount,
            int categoryCount,
            String uploadedFilePath,
            String csvFilePath,
            List<NaverCategory> categories
    ) {
        naverCategoryVersionRepository.deactivateActiveVersions();
        NaverCategoryVersion version = naverCategoryVersionRepository.save(NaverCategoryVersion.createActive(
                filename,
                sourceRowCount,
                categoryCount,
                uploadedFilePath,
                csvFilePath,
                Instant.now()
        ));

        for (NaverCategory category : categories) {
            category.assignVersionId(version.getId());
        }
        naverCategoryRepository.saveAll(categories);
        return version;
    }
}
