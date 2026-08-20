package com.be.qna.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.be.global.exception.BusinessException;
import com.be.qna.domain.QnaQuestion;
import com.be.qna.domain.QnaFaq;
import com.be.qna.repository.QnaFaqRepository;
import com.be.qna.repository.QnaQuestionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QnaServiceTest {
    private QnaFaqRepository faqRepository;
    private QnaQuestionRepository questionRepository;
    private QnaService qnaService;

    @BeforeEach
    void setUp() {
        faqRepository = mock(QnaFaqRepository.class);
        questionRepository = mock(QnaQuestionRepository.class);
        qnaService = new QnaService(faqRepository, questionRepository);
    }

    @Test
    void returnsActiveFaq() {
        QnaFaq faq = QnaFaq.create("질문", "답변", 0);
        when(faqRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(faq));

        QnaFaq result = qnaService.getActiveFaq(3L);

        assertSame(faq, result);
    }

    @Test
    void doesNotReturnInactiveFaqToUser() {
        when(faqRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> qnaService.getActiveFaq(3L));
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
