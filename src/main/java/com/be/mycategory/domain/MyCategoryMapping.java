package com.be.mycategory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "my_category_mappings",
        indexes = {
                @Index(name = "idx_my_category_mappings_user_key", columnList = "user_key"),
                @Index(name = "idx_my_category_mappings_version_id", columnList = "version_id"),
                @Index(name = "idx_my_category_mappings_my_category_code", columnList = "my_category_code"),
                @Index(name = "idx_my_category_mappings_naver_category_code", columnList = "naver_category_code")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_my_category_mappings_version_my_category",
                        columnNames = {"version_id", "my_category_code"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyCategoryMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "user_key", nullable = false, length = 100)
    private String userKey;

    @Column(name = "my_category_code", nullable = false, length = 100)
    private String myCategoryCode;

    @Column(name = "naver_category_value", nullable = false, length = 500)
    private String naverCategoryValue;

    @Column(name = "naver_category_id")
    private Long naverCategoryId;

    @Column(name = "naver_category_code", length = 30)
    private String naverCategoryCode;

    @Column(name = "naver_category_full_path", length = 500)
    private String naverCategoryFullPath;

    private MyCategoryMapping(
            Long versionId,
            String userKey,
            String myCategoryCode,
            String naverCategoryValue,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath
    ) {
        this.versionId = versionId;
        this.userKey = userKey;
        this.myCategoryCode = myCategoryCode;
        this.naverCategoryValue = naverCategoryValue;
        this.naverCategoryId = naverCategoryId;
        this.naverCategoryCode = naverCategoryCode;
        this.naverCategoryFullPath = naverCategoryFullPath;
    }

    public static MyCategoryMapping create(
            String userKey,
            String myCategoryCode,
            String naverCategoryValue,
            Long naverCategoryId,
            String naverCategoryCode,
            String naverCategoryFullPath
    ) {
        return new MyCategoryMapping(
                null,
                userKey,
                myCategoryCode,
                naverCategoryValue,
                naverCategoryId,
                naverCategoryCode,
                naverCategoryFullPath
        );
    }

    public void assignVersionId(Long versionId) {
        this.versionId = versionId;
    }
}
