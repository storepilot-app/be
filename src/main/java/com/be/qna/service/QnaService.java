package com.be.qna.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.qna.domain.QnaFaq;
import com.be.qna.domain.QnaQuestion;
import com.be.qna.dto.QnaFaqSaveRequest;
import com.be.qna.dto.QnaQuestionAnswerRequest;
import com.be.qna.dto.QnaQuestionCreateRequest;
import com.be.qna.repository.QnaFaqRepository;
import com.be.qna.repository.QnaQuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnaService {
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int FAQ_QUESTION_MAX_LENGTH = 300;
    private static final int CONTENT_MAX_LENGTH = 5000;
    private static final int ANSWER_MAX_LENGTH = 5000;

    private final QnaFaqRepository qnaFaqRepository;
    private final QnaQuestionRepository qnaQuestionRepository;

    public List<QnaFaq> getActiveFaqs() {
        return qnaFaqRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
    }

    public List<QnaFaq> getAllFaqs() {
        return qnaFaqRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public QnaFaq getActiveFaq(Long faqId) {
        return qnaFaqRepository.findByIdAndActiveTrue(faqId)
                .orElseThrow(() -> invalid("자주 묻는 질문을 찾을 수 없습니다."));
    }

    public List<QnaQuestion> getMyQuestions(Long userId) {
        validateUserId(userId);
        return qnaQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public QnaQuestion getMyQuestion(Long userId, Long questionId) {
        validateUserId(userId);
        return qnaQuestionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> invalid("문의를 찾을 수 없습니다."));
    }

    public List<QnaQuestion> getAllQuestions() {
        return qnaQuestionRepository.findAllByOrderByCreatedAtDesc();
    }

    public QnaQuestion getQuestion(Long questionId) {
        return qnaQuestionRepository.findById(questionId)
                .orElseThrow(() -> invalid("문의를 찾을 수 없습니다."));
    }

    @Transactional
    public QnaFaq createFaq(QnaFaqSaveRequest request) {
        if (request == null) {
            throw invalid("자주 묻는 질문 내용이 필요합니다.");
        }

        String question = required(request.question(), "질문을 입력해 주세요.");
        String answer = required(request.answer(), "답변을 입력해 주세요.");
        int sortOrder = request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder());
        validateMaxLength(question, FAQ_QUESTION_MAX_LENGTH, "질문은 300자 이하로 입력해 주세요.");
        validateMaxLength(answer, ANSWER_MAX_LENGTH, "답변은 5000자 이하로 입력해 주세요.");

        return qnaFaqRepository.save(QnaFaq.create(question, answer, sortOrder));
    }

    @Transactional
    public QnaFaq updateFaq(Long faqId, QnaFaqSaveRequest request) {
        if (request == null) {
            throw invalid("자주 묻는 질문 내용이 필요합니다.");
        }

        String question = required(request.question(), "질문을 입력해 주세요.");
        String answer = required(request.answer(), "답변을 입력해 주세요.");
        int sortOrder = request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder());
        validateMaxLength(question, FAQ_QUESTION_MAX_LENGTH, "질문은 300자 이하로 입력해 주세요.");
        validateMaxLength(answer, ANSWER_MAX_LENGTH, "답변은 5000자 이하로 입력해 주세요.");

        QnaFaq faq = getFaq(faqId);
        faq.update(question, answer, sortOrder);
        return faq;
    }

    @Transactional
    public QnaFaq activateFaq(Long faqId) {
        QnaFaq faq = getFaq(faqId);
        faq.activate();
        return faq;
    }

    @Transactional
    public QnaFaq deactivateFaq(Long faqId) {
        QnaFaq faq = getFaq(faqId);
        faq.deactivate();
        return faq;
    }

    @Transactional
    public QnaQuestion createQuestion(Long userId, QnaQuestionCreateRequest request) {
        validateUserId(userId);
        if (request == null) {
            throw invalid("문의 내용이 필요합니다.");
        }

        String title = required(request.title(), "문의 제목을 입력해 주세요.");
        String content = required(request.content(), "문의 내용을 입력해 주세요.");
        validateMaxLength(title, TITLE_MAX_LENGTH, "문의 제목은 200자 이하로 입력해 주세요.");
        validateMaxLength(content, CONTENT_MAX_LENGTH, "문의 내용은 5000자 이하로 입력해 주세요.");

        return qnaQuestionRepository.save(QnaQuestion.create(userId, title, content));
    }

    @Transactional
    public void deleteMyQuestion(Long userId, Long questionId) {
        QnaQuestion question = getMyQuestion(userId, questionId);
        qnaQuestionRepository.delete(question);
    }

    @Transactional
    public QnaQuestion answerQuestion(Long adminUserId, Long questionId, QnaQuestionAnswerRequest request) {
        validateUserId(adminUserId);
        if (request == null) {
            throw invalid("답변 내용이 필요합니다.");
        }

        String answer = required(request.answer(), "답변 내용을 입력해 주세요.");
        validateMaxLength(answer, ANSWER_MAX_LENGTH, "답변 내용은 5000자 이하로 입력해 주세요.");

        QnaQuestion question = getQuestion(questionId);
        question.answer(adminUserId, answer);
        return question;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    public QnaFaq getFaq(Long faqId) {
        return qnaFaqRepository.findById(faqId)
                .orElseThrow(() -> invalid("자주 묻는 질문을 찾을 수 없습니다."));
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private void validateMaxLength(String value, int maxLength, String message) {
        if (value.length() > maxLength) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_QNA_REQUEST, message);
    }
}
