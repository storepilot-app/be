package com.be.trainingproductrequest.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.trainingproductrequest.domain.TrainingProductRequest;
import com.be.trainingproductrequest.dto.TrainingProductRequestListResponse;
import com.be.trainingproductrequest.dto.TrainingProductRequestResponse;
import com.be.trainingproductrequest.service.TrainingProductRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/training-product-requests")
@Tag(name = "카테고리 학습 요청", description = "사용자 기존 상품 파일 접수 API")
@RequiredArgsConstructor
public class TrainingProductRequestController {
    private final TrainingProductRequestService trainingProductRequestService;

    @Operation(summary = "기존 상품을 이용한 카테고리 학습 요청")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<TrainingProductRequestResponse> submit(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "기존 상품 데이터가 담긴 엑셀 파일", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        TrainingProductRequest request = trainingProductRequestService.submit(
                loginUser.id(),
                loginUser.email(),
                file
        );
        return CommonResponse.success(
                TrainingProductRequestResponse.from(request),
                "카테고리 학습 요청이 접수되었습니다. 확인 후 학습을 진행하겠습니다."
        );
    }

    @Operation(summary = "내 카테고리 학습 요청 목록 조회")
    @GetMapping
    public CommonResponse<TrainingProductRequestListResponse> getMyRequests(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return CommonResponse.success(TrainingProductRequestListResponse.from(
                trainingProductRequestService.getMyRequests(loginUser.id())
        ));
    }
}
