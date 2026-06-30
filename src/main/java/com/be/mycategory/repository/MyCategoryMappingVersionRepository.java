package com.be.mycategory.repository;

import com.be.mycategory.domain.MyCategoryMappingVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MyCategoryMappingVersionRepository extends JpaRepository<MyCategoryMappingVersion, Long> {
    Optional<MyCategoryMappingVersion> findFirstByUserKeyAndActiveTrueOrderByUploadedAtDesc(String userKey);

    void deleteByUserKey(String userKey);

    @Modifying
    @Query("update MyCategoryMappingVersion v set v.active = false where v.userKey = :userKey and v.active = true")
    void deactivateActiveVersions(@Param("userKey") String userKey);
}
