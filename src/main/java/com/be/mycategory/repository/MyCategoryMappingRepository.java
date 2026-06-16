package com.be.mycategory.repository;

import com.be.mycategory.domain.MyCategoryMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyCategoryMappingRepository extends JpaRepository<MyCategoryMapping, Long> {
    List<MyCategoryMapping> findByUserKeyAndVersionId(String userKey, Long versionId);

    Optional<MyCategoryMapping> findFirstByUserKeyAndVersionIdAndMyCategoryCode(String userKey, Long versionId, String myCategoryCode);

    long countByVersionId(Long versionId);
}
