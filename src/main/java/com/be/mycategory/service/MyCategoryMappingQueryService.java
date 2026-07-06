package com.be.mycategory.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.domain.MyCategoryMapping;
import com.be.mycategory.domain.MyCategoryMappingVersion;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.mycategory.repository.MyCategoryMappingVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyCategoryMappingQueryService {
    private final MyCategoryMappingRepository myCategoryMappingRepository;
    private final MyCategoryMappingVersionRepository myCategoryMappingVersionRepository;

    public List<MyCategoryMapping> getResolvedMappings(String userKey) {
        MyCategoryMappingVersion activeVersion = getRequiredActiveVersion(userKey);
        return myCategoryMappingRepository
                .findByUserKeyAndVersionId(userKey, activeVersion.getId())
                .stream()
                .filter(this::hasResolvedNaverCategory)
                .toList();
    }

    public MyCategoryMapping getRequiredResolvedMapping(String userKey, String myCategoryCode) {
        MyCategoryMappingVersion activeVersion = getRequiredActiveVersion(userKey);
        return myCategoryMappingRepository
                .findFirstByUserKeyAndVersionIdAndMyCategoryCode(
                        userKey,
                        activeVersion.getId(),
                        myCategoryCode
                )
                .filter(this::hasResolvedNaverCategory)
                .orElseThrow(() -> invalid("마이카테고리 코드에 대응하는 네이버 카테고리 매핑이 없습니다."));
    }

    private MyCategoryMappingVersion getRequiredActiveVersion(String userKey) {
        return myCategoryMappingVersionRepository
                .findFirstByUserKeyAndActiveTrueOrderByUploadedAtDesc(userKey)
                .orElseThrow(() -> invalid("활성화된 마이카테고리 매핑 버전이 없습니다."));
    }

    private boolean hasResolvedNaverCategory(MyCategoryMapping mapping) {
        return mapping.getNaverCategoryId() != null
                && mapping.getNaverCategoryCode() != null
                && !mapping.getNaverCategoryCode().isBlank()
                && mapping.getNaverCategoryFullPath() != null
                && !mapping.getNaverCategoryFullPath().isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE, message);
    }
}
