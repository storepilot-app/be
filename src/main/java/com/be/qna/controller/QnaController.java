package com.be.qna.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.qna.domain.QnaQuestion;
import com.be.qna.dto.QnaFaqListResponse;
import com.be.qna.dto.QnaFaqResponse;
import com.be.qna.dto.QnaQuestionCreateRequest;
import com.be.qna.dto.QnaQuestionListResponse;
import com.be.qna.dto.QnaQuestionResponse;
import com.be.qna.service.QnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qna")
@Tag(name = "QnA", description = "자주 묻는 질문 조회 및 사용자 1:1 문의 API")
@RequiredArgsConstructor
public class QnaController {
    private final QnaService qnaService;

    @Operation(summary = "자주 묻는 질문 목록 조회")
    @GetMapping("/faqs")
    public CommonResponse<QnaFaqListResponse> getFaqs() {
        return CommonResponse.success(QnaFaqListResponse.from(qnaService.getActiveFaqs()));
    }

    @Operation(summary = "자주 묻는 질문 상세 조회")
    @GetMapping("/faqs/{faqId}")
    public CommonResponse<QnaFaqResponse> getFaq(@PathVariable Long faqId) {
        return CommonResponse.success(QnaFaqResponse.from(qnaService.getActiveFaq(faqId)));
    }

    @Operation(summary = "내 1:1 문의 목록 조회")
    @GetMapping("/questions")
    public CommonResponse<QnaQuestionListResponse> getMyQuestions(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return CommonResponse.success(QnaQuestionListResponse.from(qnaService.getMyQuestions(loginUser.id())));
    }

    @Operation(summary = "내 1:1 문의 상세 조회")
    @GetMapping("/questions/{questionId}")
    public CommonResponse<QnaQuestionResponse> getMyQuestion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long questionId
    ) {
        return CommonResponse.success(QnaQuestionResponse.from(qnaService.getMyQuestion(loginUser.id(), questionId)));
    }

    @Operation(summary = "1:1 문의 등록")
    @PostMapping(value = "/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<QnaQuestionResponse> createQuestion(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody QnaQuestionCreateRequest request
    ) {
        QnaQuestion question = qnaService.createQuestion(loginUser.id(), request);
        return CommonResponse.success(QnaQuestionResponse.from(question), "문의가 등록되었습니다.");
    }

    @Operation(summary = "내 1:1 문의 삭제")
    @DeleteMapping("/questions/{questionId}")
    public CommonResponse<Void> deleteMyQuestion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long questionId
    ) {
        qnaService.deleteMyQuestion(loginUser.id(), questionId);
        return CommonResponse.success(null, "문의가 삭제되었습니다.");
    }
}
