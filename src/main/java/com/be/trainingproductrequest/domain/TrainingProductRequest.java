package com.be.trainingproductrequest.domain;

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
        name = "training_product_requests",
        indexes = @Index(name = "idx_training_product_requests_user_created", columnList = "user_id, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingProductRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true, length = 100)
    private String storedFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "product_count", nullable = false)
    private int productCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private TrainingProductRequest(
            Long userId,
            String userEmail,
            String originalFilename,
            String storedFilename,
            long fileSize,
            int productCount
    ) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.fileSize = fileSize;
        this.productCount = productCount;
        this.createdAt = Instant.now();
    }

    public static TrainingProductRequest create(
            Long userId,
            String userEmail,
            String originalFilename,
            String storedFilename,
            long fileSize,
            int productCount
    ) {
        return new TrainingProductRequest(
                userId,
                userEmail,
                originalFilename,
                storedFilename,
                fileSize,
                productCount
        );
    }
}
