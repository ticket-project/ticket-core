package com.ticket.core.api.error;

import com.ticket.support.error.CommonErrorCode;
import com.ticket.support.error.ErrorCode;
import com.ticket.support.error.ErrorDefinition;
import com.ticket.support.error.ErrorStatus;

/**
 * core-api가 소유하는 오류 카탈로그다. 기능 오류를 다시 매핑하기 위한 타입이 아니라,
 * Spring MVC와 Spring Security가 ErrorDefinition 없이 발생시킨 실패를 프로젝트의 오류 응답으로
 * 표현하기 위한 고정 카탈로그다.
 *
 * <p>기능 오류를 추가할 때 이 enum과 GlobalExceptionHandler는 수정하지 않는다.
 */
public enum ApiErrorType implements ErrorDefinition {

    INVALID_REQUEST(
            ErrorStatus.BAD_REQUEST,
            CommonErrorCode.E400,
            "요청이 올바르지 않습니다."
    ),
    AUTHENTICATION_REQUIRED(
            ErrorStatus.UNAUTHORIZED,
            CommonErrorCode.E1000,
            "로그인이 필요합니다."
    ),
    ACCESS_DENIED(
            ErrorStatus.FORBIDDEN,
            CommonErrorCode.E1001,
            "권한이 없습니다."
    ),
    API_NOT_FOUND(
            ErrorStatus.NOT_FOUND,
            CommonErrorCode.E404,
            "요청한 API를 찾을 수 없습니다."
    ),
    INTERNAL_SERVER_ERROR(
            ErrorStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.E500,
            "일시적인 오류가 발생했습니다."
    );

    private final ErrorStatus status;
    private final ErrorCode errorCode;
    private final String message;

    ApiErrorType(final ErrorStatus status, final ErrorCode errorCode, final String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public ErrorStatus getStatus() {
        return status;
    }

    @Override
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
