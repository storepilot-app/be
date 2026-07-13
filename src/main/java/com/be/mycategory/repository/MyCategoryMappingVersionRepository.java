package com.be.mycategory.repository;

import com.be.mycategory.domain.MyCategoryMappingVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MyCategoryMappingVersionRepository extends JpaRepository<MyCategoryMappingVersion, Long> {
    Optional<MyCategoryMappingVersion> findFirstByUserIdAndActiveTrueOrderByUploadedAtDesc(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("update MyCategoryMappingVersion v set v.active = false where v.userId = :userId and v.active = true")
    void deactivateActiveVersions(@Param("userId") Long userId);
}
