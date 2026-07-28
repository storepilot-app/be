package com.be.qna.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class QnaQuestionTest {
    @Test
    void createsWaitingQuestionAndMarksAnswered() {
        QnaQuestion question = QnaQuestion.create(1L, "문의 제목", "문의 내용");

        assertEquals(QnaQuestionStatus.WAITING, question.getStatus());
        assertNull(question.getAnswer());
        assertNotNull(question.getCreatedAt());

        question.answer(99L, "답변 내용");

        assertEquals(QnaQuestionStatus.ANSWERED, question.getStatus());
        assertEquals("답변 내용", question.getAnswer());
        assertEquals(99L, question.getAnsweredBy());
        assertNotNull(question.getAnsweredAt());
    }
}
