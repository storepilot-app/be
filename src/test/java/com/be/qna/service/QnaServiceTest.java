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
    @Test
    void followUpPreservesConversationAndReopensQuestion() {
        QnaQuestion question = QnaQuestion.create(1L, "제목", "원래 질문");
        question.answer(2L, "첫 답변");
        when(questionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(question));
        qnaService.followUpQuestion(1L, 7L, new com.be.qna.dto.QnaQuestionFollowUpRequest(" 재질문 "));
        org.junit.jupiter.api.Assertions.assertEquals("원래 질문", question.getContent());
        org.junit.jupiter.api.Assertions.assertEquals("첫 답변", question.getMessages().get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(question.getMessages().get(0).isAdmin());
        org.junit.jupiter.api.Assertions.assertEquals("재질문", question.getMessages().get(1).getContent());
        org.junit.jupiter.api.Assertions.assertNull(question.getAnswer());
        org.junit.jupiter.api.Assertions.assertEquals(com.be.qna.domain.QnaQuestionStatus.WAITING, question.getStatus());
        question.answer(2L, "두 번째 답변");
        question.followUp("추가 질문");
        org.junit.jupiter.api.Assertions.assertEquals(4, question.getMessages().size());
        org.junit.jupiter.api.Assertions.assertEquals("두 번째 답변", question.getMessages().get(2).getContent());
    }

    @Test
    void rejectsFollowUpFromAnotherUser() {
        when(questionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> qnaService.followUpQuestion(1L, 7L,
                new com.be.qna.dto.QnaQuestionFollowUpRequest("재질문")));
    }

    @Test
    void rejectsEmptyAndOversizedFollowUps() {
        QnaQuestion question = QnaQuestion.create(1L, "제목", "내용");
        when(questionRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(question));
        for (String content : new String[]{" ", "a".repeat(5001)}) {
            assertThrows(BusinessException.class, () -> qnaService.followUpQuestion(1L, 7L,
                    new com.be.qna.dto.QnaQuestionFollowUpRequest(content)));
        }
        org.junit.jupiter.api.Assertions.assertTrue(question.getMessages().isEmpty());
    }
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
