package com.be.trainingproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기존 상품 인덱스 추가 결과")
public record ProductIndexAppendResponse(
        @Schema(description = "업로드 파일 수") int sourceCount,
        @Schema(description = "상품명이 있는 원본 행 수") int sourceRowCount,
        @Schema(description = "마이카테고리 매핑까지 완료된 행 수") int validRowCount,
        @Schema(description = "마이카테고리 매핑이 없는 행 수") int unmappedRowCount,
        @Schema(description = "기존 상품 인덱스에 추가/갱신 요청한 상품 수") int appendedProductCount,
        @Schema(description = "새로 추가된 상품 수") int insertedProductCount,
        @Schema(description = "이미 존재해서 갱신된 상품 수") int updatedProductCount,
        @Schema(description = "현재 인덱싱된 전체 상품 수") int indexedProductCount,
        @Schema(description = "처리 결과 메시지") String message
) {
}
