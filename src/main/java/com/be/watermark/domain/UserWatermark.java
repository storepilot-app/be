package com.be.watermark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "user_watermarks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_watermarks_user_id", columnNames = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWatermark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] imageData;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WatermarkPosition position;

    @Column(nullable = false)
    private int opacity;

    @Column(name = "size_percent", nullable = false)
    private int sizePercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UserWatermark(
            Long userId,
            byte[] imageData,
            String contentType,
            String originalFilename,
            WatermarkPosition position,
            int opacity,
            int sizePercent
    ) {
        this.userId = userId;
        update(imageData, contentType, originalFilename, position, opacity, sizePercent);
    }

    public static UserWatermark create(
            Long userId,
            byte[] imageData,
            String contentType,
            String originalFilename,
            WatermarkPosition position,
            int opacity,
            int sizePercent
    ) {
        return new UserWatermark(userId, imageData, contentType, originalFilename, position, opacity, sizePercent);
    }

    public void update(
            byte[] imageData,
            String contentType,
            String originalFilename,
            WatermarkPosition position,
            int opacity,
            int sizePercent
    ) {
        this.imageData = imageData;
        this.contentType = contentType;
        this.originalFilename = originalFilename;
        this.fileSize = imageData.length;
        this.position = position;
        this.opacity = opacity;
        this.sizePercent = sizePercent;
        this.updatedAt = Instant.now();
    }
}
