package com.be.qna.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "qna_questions",
        indexes = {
                @Index(name = "idx_qna_questions_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_qna_questions_status_created", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaQuestion {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "qna_question_messages", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "message_order")
    private List<QnaMessage> messages = new ArrayList<>();

    public void followUp(String content) {
        Instant now = Instant.now();
        if (answer != null) {
            messages.add(new QnaMessage(answer, true, answeredAt));
        }
        messages.add(new QnaMessage(content, false, now));
        answer = null;
        answeredBy = null;
        answeredAt = null;
        status = QnaQuestionStatus.WAITING;
        updatedAt = now;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QnaQuestionStatus status;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_by")
    private Long answeredBy;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private QnaQuestion(
            Long userId,
            String title,
            String content,
            QnaQuestionStatus status,
            String answer,
            Long answeredBy,
            Instant answeredAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.answer = answer;
        this.answeredBy = answeredBy;
        this.answeredAt = answeredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QnaQuestion create(Long userId, String title, String content) {
        Instant now = Instant.now();
        return new QnaQuestion(
                userId,
                title,
                content,
                QnaQuestionStatus.WAITING,
                null,
                null,
                null,
                now,
                now
        );
    }

    public void answer(Long adminUserId, String answer) {
        Instant now = Instant.now();
        this.answer = answer;
        this.answeredBy = adminUserId;
        this.answeredAt = now;
        this.status = QnaQuestionStatus.ANSWERED;
        this.updatedAt = now;
    }
}
