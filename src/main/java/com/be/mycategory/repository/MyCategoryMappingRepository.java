package com.be.mycategory.repository;

import com.be.mycategory.domain.MyCategoryMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyCategoryMappingRepository extends JpaRepository<MyCategoryMapping, Long> {
    List<MyCategoryMapping> findByUserIdAndVersionId(Long userId, Long versionId);

    Optional<MyCategoryMapping> findFirstByUserIdAndVersionIdAndMyCategoryCode(Long userId, Long versionId, String myCategoryCode);

    List<MyCategoryMapping> findByUserIdAndNaverCategoryCodeIn(Long userId, Collection<String> naverCategoryCodes);

    long countByVersionId(Long versionId);

    void deleteByUserId(Long userId);
}
