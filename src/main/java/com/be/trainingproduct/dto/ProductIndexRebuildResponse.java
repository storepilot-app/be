package com.be.trainingproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기존 상품 FAISS 인덱스 재생성 결과")
public record ProductIndexRebuildResponse(
        @Schema(description = "마이카테고리 매핑에 사용한 사용자 ID") Long userId,
        @Schema(description = "업로드한 엑셀 파일 수") int sourceCount,
        @Schema(description = "엑셀에서 읽은 전체 상품 행 수") int sourceRowCount,
        @Schema(description = "카테고리 매핑에 성공한 유효 행 수") int validRowCount,
        @Schema(description = "네이버 카테고리로 변환하지 못한 행 수") int unmappedRowCount,
        @Schema(description = "최종 인덱싱된 상품 수") int indexedProductCount,
        @Schema(description = "제외된 중복 행 수") int duplicateRowCount,
        @Schema(description = "동일 상품명에 서로 다른 카테고리가 존재한 건수") int conflictingTitleCount,
        @Schema(description = "처리 결과 메시지") String message
) {
}
