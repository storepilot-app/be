package com.be.global.exception;

import com.be.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = resolveStatus(exception.getErrorCode());
        return ResponseEntity.status(status)
                .body(CommonResponse.fail(exception.getErrorCode().name(), exception.getMessage()));
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        if (errorCode == ErrorCode.INVALID_EXCEL_FILE
                || errorCode == ErrorCode.INVALID_NAVER_CATEGORY_FILE
                || errorCode == ErrorCode.INVALID_MY_CATEGORY_MAPPING_FILE
                || errorCode == ErrorCode.AUTH_INVALID
                || errorCode == ErrorCode.INVALID_WATERMARK_REQUEST
                || errorCode == ErrorCode.INVALID_QNA_REQUEST) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode == ErrorCode.AUTH_UNAUTHORIZED) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (errorCode == ErrorCode.AUTH_FORBIDDEN) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(CommonResponse.fail(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        "필수 요청 값이 누락되었습니다: " + exception.getParameterName()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handleMaxUploadSizeExceeded() {
        return ResponseEntity.badRequest()
                .body(CommonResponse.fail(
                        ErrorCode.INVALID_EXCEL_FILE.name(),
                        "업로드 가능한 파일 크기를 초과했습니다."
                ));
    }
}
