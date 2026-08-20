package com.be.qna.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.global.exception.BusinessException;
import com.be.qna.domain.QnaQuestion;
import com.be.qna.repository.QnaFaqRepository;
import com.be.qna.repository.QnaQuestionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QnaServiceTest {
    private QnaQuestionRepository questionRepository;
    private QnaService qnaService;

    @BeforeEach
    void setUp() {
        QnaFaqRepository faqRepository = mock(QnaFaqRepository.class);
        questionRepository = mock(QnaQuestionRepository.class);
        qnaService = new QnaService(faqRepository, questionRepository);
    }

    @Test
    void deletesQuestionOwnedByUser() {
        QnaQuestion question = QnaQuestion.create(1L, "문의 제목", "문의 내용");
        when(questionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(question));

        qnaService.deleteMyQuestion(1L, 7L);

        verify(questionRepository).delete(question);
    }

    @Test
    void doesNotDeleteQuestionOwnedByAnotherUser() {
        when(questionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> qnaService.deleteMyQuestion(1L, 7L));

        verify(questionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
