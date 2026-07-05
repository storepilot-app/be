package com.be.navercategory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "naver_category_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NaverCategoryVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "category_count", nullable = false)
    private int categoryCount;

    @Column(name = "uploaded_file_path", nullable = false, length = 1000)
    private String uploadedFilePath;

    @Column(name = "csv_file_path", nullable = false, length = 1000)
    private String csvFilePath;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    private NaverCategoryVersion(
            String sourceFilename,
            int rowCount,
            int categoryCount,
            String uploadedFilePath,
            String csvFilePath,
            Instant uploadedAt,
            boolean active
    ) {
        this.sourceFilename = sourceFilename;
        this.rowCount = rowCount;
        this.categoryCount = categoryCount;
        this.uploadedFilePath = uploadedFilePath;
        this.csvFilePath = csvFilePath;
        this.uploadedAt = uploadedAt;
        this.active = active;
    }

    public static NaverCategoryVersion createActive(
            String sourceFilename,
            int rowCount,
            int categoryCount,
            String uploadedFilePath,
            String csvFilePath,
            Instant uploadedAt
    ) {
        return new NaverCategoryVersion(
                sourceFilename,
                rowCount,
                categoryCount,
                uploadedFilePath,
                csvFilePath,
                uploadedAt,
                true
        );
    }
}
