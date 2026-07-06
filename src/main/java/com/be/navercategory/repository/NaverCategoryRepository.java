package com.be.navercategory.repository;

import com.be.navercategory.domain.NaverCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NaverCategoryRepository extends JpaRepository<NaverCategory, Long> {
    List<NaverCategory> findByVersionId(Long versionId);

    long countByVersionId(Long versionId);
}
