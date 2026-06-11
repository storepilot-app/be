package com.be.navercategory.repository;

import com.be.navercategory.domain.NaverCategoryVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NaverCategoryVersionRepository extends JpaRepository<NaverCategoryVersion, Long> {
    Optional<NaverCategoryVersion> findFirstByActiveTrueOrderByUploadedAtDesc();

    @Modifying
    @Query("update NaverCategoryVersion v set v.active = false where v.active = true")
    void deactivateActiveVersions();
}
