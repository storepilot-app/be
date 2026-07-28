package com.be.qna.controller;

import com.be.auth.domain.UserRole;
import com.be.auth.security.LoginUser;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.response.CommonResponse;
import com.be.qna.domain.QnaFaq;
import com.be.qna.domain.QnaQuestion;
import com.be.qna.dto.QnaFaqListResponse;
import com.be.qna.dto.QnaFaqResponse;
import com.be.qna.dto.QnaFaqSaveRequest;
import com.be.qna.dto.QnaQuestionAnswerRequest;
import com.be.qna.dto.QnaQuestionListResponse;
import com.be.qna.dto.QnaQuestionResponse;
import com.be.qna.service.QnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/qna")
@Tag(name = "관리자 QnA", description = "관리자 1:1 문의 조회 및 답변 API")
@RequiredArgsConstructor
public class AdminQnaController {
    private final QnaService qnaService;

    @Operation(summary = "전체 FAQ 목록 조회")
    @GetMapping("/faqs")
    public CommonResponse<QnaFaqListResponse> getFaqs(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        return CommonResponse.success(QnaFaqListResponse.from(qnaService.getAllFaqs()));
    }

    @Operation(summary = "FAQ 등록")
    @PostMapping(value = "/faqs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<QnaFaqResponse> createFaq(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody QnaFaqSaveRequest request
    ) {
        requireAdmin(loginUser);
        QnaFaq faq = qnaService.createFaq(request);
        return CommonResponse.success(QnaFaqResponse.from(faq), "FAQ가 등록되었습니다.");
    }

    @Operation(summary = "FAQ 수정")
    @PutMapping(value = "/faqs/{faqId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<QnaFaqResponse> updateFaq(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long faqId,
            @RequestBody QnaFaqSaveRequest request
    ) {
        requireAdmin(loginUser);
        QnaFaq faq = qnaService.updateFaq(faqId, request);
        return CommonResponse.success(QnaFaqResponse.from(faq), "FAQ가 수정되었습니다.");
    }

    @Operation(summary = "FAQ 노출")
    @PatchMapping("/faqs/{faqId}/activate")
    public CommonResponse<QnaFaqResponse> activateFaq(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long faqId
    ) {
        requireAdmin(loginUser);
        QnaFaq faq = qnaService.activateFaq(faqId);
        return CommonResponse.success(QnaFaqResponse.from(faq), "FAQ가 노출 처리되었습니다.");
    }

    @Operation(summary = "FAQ 숨김")
    @PatchMapping("/faqs/{faqId}/deactivate")
    public CommonResponse<QnaFaqResponse> deactivateFaq(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long faqId
    ) {
        requireAdmin(loginUser);
        QnaFaq faq = qnaService.deactivateFaq(faqId);
        return CommonResponse.success(QnaFaqResponse.from(faq), "FAQ가 숨김 처리되었습니다.");
    }

    @Operation(summary = "전체 1:1 문의 목록 조회")
    @GetMapping("/questions")
    public CommonResponse<QnaQuestionListResponse> getQuestions(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        return CommonResponse.success(QnaQuestionListResponse.from(qnaService.getAllQuestions()));
    }

    @Operation(summary = "1:1 문의 상세 조회")
    @GetMapping("/questions/{questionId}")
    public CommonResponse<QnaQuestionResponse> getQuestion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long questionId
    ) {
        requireAdmin(loginUser);
        return CommonResponse.success(QnaQuestionResponse.from(qnaService.getQuestion(questionId)));
    }

    @Operation(summary = "1:1 문의 답변 등록")
    @PostMapping(value = "/questions/{questionId}/answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<QnaQuestionResponse> answerQuestion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long questionId,
            @RequestBody QnaQuestionAnswerRequest request
    ) {
        requireAdmin(loginUser);
        QnaQuestion question = qnaService.answerQuestion(loginUser.id(), questionId, request);
        return CommonResponse.success(QnaQuestionResponse.from(question), "답변이 등록되었습니다.");
    }

    private void requireAdmin(LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "관리자만 사용할 수 있는 기능입니다.");
        }
    }
}
