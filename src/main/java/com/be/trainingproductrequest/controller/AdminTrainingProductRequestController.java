package com.be.trainingproductrequest.controller;

import com.be.global.response.CommonResponse;
import com.be.trainingproductrequest.dto.TrainingProductRequestFile;
import com.be.trainingproductrequest.dto.TrainingProductRequestListResponse;
import com.be.trainingproductrequest.service.TrainingProductRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/training-product-requests")
@Tag(name = "관리자 카테고리 학습 요청", description = "사용자가 제출한 기존 상품 파일 관리 API")
@RequiredArgsConstructor
public class AdminTrainingProductRequestController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final TrainingProductRequestService trainingProductRequestService;

    @Operation(summary = "전체 카테고리 학습 요청 목록 조회")
    @GetMapping
    public CommonResponse<TrainingProductRequestListResponse> getRequests() {
        return CommonResponse.success(TrainingProductRequestListResponse.from(
                trainingProductRequestService.getAllRequests()
        ));
    }

    @Operation(summary = "카테고리 학습 요청 파일 다운로드")
    @GetMapping("/{requestId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long requestId) {
        TrainingProductRequestFile file = trainingProductRequestService.getRequestFile(requestId);
        String disposition = ContentDisposition.attachment()
                .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }
}
