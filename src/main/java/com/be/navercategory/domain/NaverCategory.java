package com.be.navercategory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "naver_categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_naver_categories_version_code",
                        columnNames = {"version_id", "category_code"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NaverCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "category_code", nullable = false, length = 30)
    private String categoryCode;

    @Column(name = "level1", nullable = false, length = 100)
    private String level1;

    @Column(name = "level2", length = 100)
    private String level2;

    @Column(name = "level3", length = 100)
    private String level3;

    @Column(name = "level4", length = 100)
    private String level4;

    @Column(name = "full_path", nullable = false, length = 500)
    private String fullPath;

    @Column(name = "search_text", nullable = false, length = 500)
    private String searchText;

    private NaverCategory(
            Long versionId,
            String categoryCode,
            String level1,
            String level2,
            String level3,
            String level4,
            String fullPath,
            String searchText
    ) {
        this.versionId = versionId;
        this.categoryCode = categoryCode;
        this.level1 = level1;
        this.level2 = level2;
        this.level3 = level3;
        this.level4 = level4;
        this.fullPath = fullPath;
        this.searchText = searchText;
    }

    public static NaverCategory create(
            String categoryCode,
            String level1,
            String level2,
            String level3,
            String level4
    ) {
        List<String> levels = List.of(level1, level2, level3, level4).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new NaverCategory(
                null,
                categoryCode,
                level1,
                level2,
                level3,
                level4,
                String.join(" > ", levels),
                String.join(" ", levels)
        );
    }

    public void assignVersionId(Long versionId) {
        this.versionId = versionId;
    }
}
