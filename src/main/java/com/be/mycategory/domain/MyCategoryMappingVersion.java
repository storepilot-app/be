package com.be.mycategory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "my_category_mapping_versions",
        indexes = {
                @Index(name = "idx_my_category_mapping_versions_user_active", columnList = "user_id, active")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyCategoryMappingVersion {
    private static final String ORIGINAL_FILE_NOT_STORED = "NOT_STORED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "mapping_count", nullable = false)
    private int mappingCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "uploaded_file_path", nullable = false, length = 1000)
    private String uploadedFilePath;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    private MyCategoryMappingVersion(
            Long userId,
            String sourceFilename,
            int rowCount,
            int mappingCount,
            int matchedCount,
            Instant uploadedAt,
            boolean active
    ) {
        this.userId = userId;
        this.sourceFilename = sourceFilename;
        this.rowCount = rowCount;
        this.mappingCount = mappingCount;
        this.matchedCount = matchedCount;
        this.uploadedFilePath = ORIGINAL_FILE_NOT_STORED;
        this.uploadedAt = uploadedAt;
        this.active = active;
    }

    public static MyCategoryMappingVersion createActive(
            Long userId,
            String sourceFilename,
            int rowCount,
            int mappingCount,
            int matchedCount,
            Instant uploadedAt
    ) {
        return new MyCategoryMappingVersion(
                userId,
                sourceFilename,
                rowCount,
                mappingCount,
                matchedCount,
                uploadedAt,
                true
        );
    }
}
