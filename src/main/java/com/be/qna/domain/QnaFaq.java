package com.be.qna.domain;

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
        name = "qna_faqs",
        indexes = {
                @Index(name = "idx_qna_faqs_active_sort", columnList = "active, sort_order")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaFaq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private QnaFaq(
            String question,
            String answer,
            int sortOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QnaFaq create(String question, String answer, int sortOrder) {
        Instant now = Instant.now();
        return new QnaFaq(question, answer, sortOrder, true, now, now);
    }

    public void update(String question, String answer, int sortOrder) {
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
