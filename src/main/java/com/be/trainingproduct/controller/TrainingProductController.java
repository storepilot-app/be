package com.be.trainingproduct.controller;

import com.be.auth.domain.UserRole;
import com.be.auth.security.LoginUser;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.response.CommonResponse;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.dto.ProductCategoryStatsResponse;
import com.be.trainingproduct.dto.ProductIndexAppendResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
import com.be.trainingproduct.service.TrainingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/training-products")
@Tag(name = "기존 상품 학습 데이터", description = "기존 상품 벡터 인덱스 재생성 및 카테고리 수정 피드백 API")
@RequiredArgsConstructor
public class TrainingProductController {
    private final TrainingProductService trainingProductService;

    @Operation(
            summary = "기존 상품 FAISS 인덱스 재생성",
            description = "상품 엑셀의 상품명과 마이카테 열을 읽고, 함께 업로드한 마이카테고리 파일의 네이버 카테고리 매핑으로 공용 FAISS 상품 인덱스를 재생성합니다."
    )
    @PostMapping(value = "/rebuild", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductIndexRebuildResponse> rebuild(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "기존 상품 데이터가 담긴 엑셀 파일 목록", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "상품 파일의 마이카테를 네이버 카테고리로 연결할 매핑 엑셀 파일", required = true)
            @RequestParam("myCategoryFile") MultipartFile myCategoryFile
    ) {
        requireAdmin(loginUser);
        ProductIndexRebuildResponse response = trainingProductService.rebuildIndex(
                loginUser.id(),
                files,
                myCategoryFile
        );
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "기존 상품 인덱스에 상품 추가",
            description = "상품 엑셀의 상품명과 마이카테 열을 읽고, 함께 업로드한 마이카테고리 파일의 네이버 카테고리 매핑으로 기존 상품 인덱스에 추가합니다."
    )
    @PostMapping(value = "/append", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductIndexAppendResponse> append(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "추가할 기존 상품 데이터가 담긴 엑셀 파일 목록", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "상품 파일의 마이카테를 네이버 카테고리로 연결할 매핑 엑셀 파일", required = true)
            @RequestParam("myCategoryFile") MultipartFile myCategoryFile
    ) {
        requireAdmin(loginUser);
        ProductIndexAppendResponse response = trainingProductService.appendProducts(
                loginUser.id(),
                files,
                myCategoryFile
        );
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "기존 상품 네이버 카테고리별 개수 조회",
            description = "최근 기존 상품 인덱스 재생성 시 저장된 네이버 카테고리별 기존 상품 개수를 조회합니다."
    )
    @GetMapping("/category-stats")
    public CommonResponse<ProductCategoryStatsResponse> categoryStats(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        ProductCategoryStatsResponse response = trainingProductService.getCategoryStats(loginUser.id());
        return CommonResponse.success(response, "기존 상품 카테고리 통계를 조회했습니다.");
    }

    @Operation(
            summary = "상품 카테고리 수정 피드백 저장",
            description = "사용자가 확정한 상품 카테고리를 MySQL에 저장하고 공용 FAISS 상품 인덱스에 즉시 반영합니다."
    )
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<ProductCategoryFeedbackResponse> feedback(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody ProductCategoryFeedbackRequest request
    ) {
        requireAdmin(loginUser);
        ProductCategoryFeedbackResponse response = trainingProductService.addFeedback(loginUser.id(), request);
        return CommonResponse.success(response, response.message());
    }

    private void requireAdmin(LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "관리자만 사용할 수 있는 기능입니다.");
        }
    }
}
