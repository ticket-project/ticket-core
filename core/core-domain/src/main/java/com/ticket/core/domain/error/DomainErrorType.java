package com.ticket.core.domain.error;

import com.ticket.support.error.CommonErrorCode;
import com.ticket.support.error.ErrorCode;
import com.ticket.support.error.ErrorDefinition;
import com.ticket.support.error.ErrorStatus;

/**
 * core-domain이 소유하는 오류 카탈로그다. 도메인 객체와 도메인 정책이 직접 판단하는 실패를 담는다.
 *
 * <p>호출부는 {@code throw new CoreException(DomainErrorType.SEAT_ALREADY_HOLD)}처럼 완성된 오류를
 * 고르기만 한다. API는 이 타입을 참조하지 않고 ErrorDefinition 계약만 직렬화한다.
 */
public enum DomainErrorType implements ErrorDefinition {

    INVALID_ARGUMENT(
            ErrorStatus.BAD_REQUEST,
            CommonErrorCode.E400,
            "요청이 올바르지 않습니다."
    ),
    AUTHENTICATION_FAILED(
            ErrorStatus.UNAUTHORIZED,
            CommonErrorCode.E1000,
            "로그인이 필요합니다."
    ),

    // 회차
    PERFORMANCE_IS_PAST(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E3001,
            "과거 공연은 예매할 수 없습니다."
    ),
    NOT_YET_RESERVE_TIME(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E3002,
            "아직 예매가 오픈되지 않았습니다."
    ),
    NOT_EXIST_AVAILABLE_SEAT(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E3003,
            "이용 가능한 좌석이 없습니다."
    ),

    // 회차 좌석
    SEAT_MISMATCH_IN_PERFORMANCE(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E4000,
            "요청한 좌석 정보와 일치하지 않습니다."
    ),
    SEAT_ALREADY_SELECTED(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E4001,
            "이미 선택된 좌석입니다."
    ),
    SEAT_NOT_OWNED(
            ErrorStatus.FORBIDDEN,
            DomainErrorCode.E4002,
            "본인이 선택한 좌석만 해제할 수 있습니다."
    ),

    // 주문
    ORDER_NOT_PENDING(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E5002,
            "결제 대기 주문만 처리할 수 있습니다."
    ),
    ORDER_NOT_OWNED(
            ErrorStatus.FORBIDDEN,
            DomainErrorCode.E5003,
            "본인 주문만 처리할 수 있습니다."
    ),

    // 선점
    SEAT_ALREADY_HOLD(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E6000,
            "좌석이 이미 선점되었습니다."
    ),
    EXCEED_HOLD_LIMIT(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E6001,
            "선점 가능한 좌석 수를 초과하였습니다."
    ),
    HOLD_BUSY(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E6003,
            "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요."
    ),

    // 공연
    NOT_SUPPORT_SHOW_SORT(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E7000,
            "지원하지 않는 정렬 조건입니다."
    );

    private final ErrorStatus status;
    private final ErrorCode errorCode;
    private final String message;

    DomainErrorType(final ErrorStatus status, final ErrorCode errorCode, final String message) {
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
