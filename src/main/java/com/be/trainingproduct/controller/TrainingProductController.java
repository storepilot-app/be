package com.be.trainingproduct.controller;

import com.be.global.response.CommonResponse;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.dto.ProductIndexRebuildResponse;
import com.be.trainingproduct.service.TrainingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
            description = "엑셀 D열의 상품명과 T열의 마이카테고리 코드를 읽어 공용 FAISS 상품 인덱스를 재생성합니다. 여러 개의 .xlsx 파일을 업로드할 수 있습니다."
    )
    @PostMapping(value = "/rebuild", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductIndexRebuildResponse> rebuild(
            @Parameter(description = "마이카테고리 매핑을 조회할 사용자 식별자", example = "uno1969", required = true)
            @RequestParam("userKey") String userKey,
            @Parameter(description = "기존 상품 데이터가 담긴 엑셀 파일 목록", required = true)
            @RequestParam("files") List<MultipartFile> files
    ) {
        ProductIndexRebuildResponse response = trainingProductService.rebuildIndex(userKey, files);
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "상품 카테고리 수정 피드백 저장",
            description = "사용자가 확정한 상품 카테고리를 MySQL에 저장하고 공용 FAISS 상품 인덱스에 즉시 반영합니다."
    )
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<ProductCategoryFeedbackResponse> feedback(
            @RequestBody ProductCategoryFeedbackRequest request
    ) {
        ProductCategoryFeedbackResponse response = trainingProductService.addFeedback(request);
        return CommonResponse.success(response, response.message());
    }
}
