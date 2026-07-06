package com.be.navercategory.repository;

import com.be.navercategory.domain.NaverCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NaverCategoryRepository extends JpaRepository<NaverCategory, Long> {
    List<NaverCategory> findByVersionId(Long versionId);

    Optional<NaverCategory> findFirstByVersionIdAndCategoryCode(Long versionId, String categoryCode);

    long countByVersionId(Long versionId);
}
