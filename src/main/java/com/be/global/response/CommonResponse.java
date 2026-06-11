package com.be.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;

@Getter
@Schema(description = "공통 응답 형식")
public class CommonResponse<T> {

    @Schema(description = "성공 여부", example = "true")
    private final boolean success;

    @Schema(description = "응답 데이터")
    private final T data;

    @Schema(description = "응답 메시지", example = "요청 성공")
    private final String message;

    @Schema(description = "응답 코드", example = "INVALID_EXCEL_FILE")
    private final String code;

    @Schema(description = "에러 상세 정보")
    private final Map<String, String> errors;

    private CommonResponse(boolean success, T data, String message, String code, Map<String, String> errors) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.code = code;
        this.errors = errors;
    }

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(true, data, "요청 성공", null, null);
    }

    public static <T> CommonResponse<T> success(T data, String message) {
        return new CommonResponse<>(true, data, message, null, null);
    }

    public static <T> CommonResponse<T> fail(String code, String message) {
        return new CommonResponse<>(false, null, message, code, null);
    }

    public static <T> CommonResponse<T> validationFail(String code, String message, Map<String, String> errors) {
        return new CommonResponse<>(false, null, message, code, errors);
    }
}
