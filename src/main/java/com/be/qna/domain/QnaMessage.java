package com.be.qna.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaMessage {
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false)
    private boolean admin;
    @Column(nullable = false)
    private Instant createdAt;

    public QnaMessage(String content, boolean admin, Instant createdAt) {
        this.content = content;
        this.admin = admin;
        this.createdAt = createdAt;
    }
}
