package com.ticket.core.app.error;

import com.ticket.support.error.CommonErrorCode;
import com.ticket.support.error.ErrorCode;
import com.ticket.support.error.ErrorDefinition;
import com.ticket.support.error.ErrorStatus;

/**
 * core-app이 소유하는 오류 카탈로그다. 조회 결과 없음, 유스케이스 입력 조합 오류, 멱등성 충돌,
 * 외부 시스템 실패를 유스케이스 의미로 번역한 실패를 담는다.
 *
 * <p>도메인 규칙이 판단하는 실패는 DomainErrorType이 소유한다. core-infra는 자체 카탈로그를 두지 않고,
 * 기술 실패를 안정적인 유스케이스 실패로 공개해야 할 때만 이 카탈로그의 오류로 의미를 바꾼다.
 */
public enum ApplicationErrorType implements ErrorDefinition {

    INVALID_INPUT(
            ErrorStatus.BAD_REQUEST,
            CommonErrorCode.E400,
            "요청이 올바르지 않습니다."
    ),
    DATA_NOT_FOUND(
            ErrorStatus.NOT_FOUND,
            CommonErrorCode.E404,
            "요청하신 정보를 찾을 수 없습니다."
    ),
    AUTHENTICATION_FAILED(
            ErrorStatus.UNAUTHORIZED,
            CommonErrorCode.E1000,
            "로그인이 필요합니다."
    ),
    ACCESS_DENIED(
            ErrorStatus.FORBIDDEN,
            CommonErrorCode.E1001,
            "권한이 없습니다."
    ),
    EXTERNAL_SERVICE_ERROR(
            ErrorStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.E500,
            "일시적인 오류가 발생했습니다."
    ),

    // 회원
    MEMBER_DUPLICATE_EMAIL(
            ErrorStatus.CONFLICT,
            ApplicationErrorCode.E2000,
            "중복된 이메일은 불가능합니다."
    ),

    // 주문
    PENDING_ORDER_ALREADY_EXISTS(
            ErrorStatus.CONFLICT,
            ApplicationErrorCode.E5004,
            "이미 진행 중인 결제 대기 주문이 있습니다."
    ),

    // 공연
    SHOW_LIKE_ALREADY_EXISTS(
            ErrorStatus.CONFLICT,
            ApplicationErrorCode.E7001,
            "이미 찜한 공연입니다."
    ),

    // 대기열 입장
    ADMISSION_TOKEN_REQUIRED(
            ErrorStatus.FORBIDDEN,
            ApplicationErrorCode.E8000,
            "대기열 입장 토큰이 필요합니다."
    ),
    ADMISSION_TOKEN_EXPIRED(
            ErrorStatus.FORBIDDEN,
            ApplicationErrorCode.E8001,
            "대기열 입장 토큰이 만료되었습니다."
    ),
    ADMISSION_TOKEN_INVALID(
            ErrorStatus.FORBIDDEN,
            ApplicationErrorCode.E8002,
            "대기열 입장 토큰이 올바르지 않습니다."
    );

    private final ErrorStatus status;
    private final ErrorCode errorCode;
    private final String message;

    ApplicationErrorType(final ErrorStatus status, final ErrorCode errorCode, final String message) {
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
