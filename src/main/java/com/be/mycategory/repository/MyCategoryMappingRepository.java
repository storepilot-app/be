package com.be.mycategory.repository;

import com.be.mycategory.domain.MyCategoryMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyCategoryMappingRepository extends JpaRepository<MyCategoryMapping, Long> {
    List<MyCategoryMapping> findByUserKeyAndVersionId(String userKey, Long versionId);

    Optional<MyCategoryMapping> findFirstByUserKeyAndVersionIdAndMyCategoryCode(String userKey, Long versionId, String myCategoryCode);

    Optional<MyCategoryMapping> findFirstByUserKeyAndNaverCategoryCode(String userKey, String naverCategoryCode);

    Optional<MyCategoryMapping> findFirstByUserKeyAndNaverCategoryFullPath(String userKey, String naverCategoryFullPath);

    long countByVersionId(Long versionId);

    void deleteByUserKey(String userKey);
}
