package com.ticket.support.error;

import lombok.Getter;

/**
 * 모듈별 오류 카탈로그로 옮기는 동안만 남겨 두는 기존 전역 오류다.
 * 새 오류는 DomainErrorType, ApplicationErrorType, ApiErrorType에 정의한다.
 *
 * @deprecated 마이그레이션이 끝나면 삭제한다.
 */
@Deprecated
@Getter
public enum ErrorType implements ErrorDefinition {
    DEFAULT_ERROR(ErrorStatus.INTERNAL_SERVER_ERROR, LegacyErrorCode.E500, "알 수 없는 에러입니다."),
    NOT_FOUND_DATA(ErrorStatus.NOT_FOUND, LegacyErrorCode.E404, "요청하신 정보를 찾을 수 없습니다."),
    INVALID_REQUEST(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E400, "요청이 올바르지 않습니다."),

    //AUTH
    AUTHENTICATION_ERROR(ErrorStatus.UNAUTHORIZED, LegacyErrorCode.E1000, "로그인이 필요합니다."),
    AUTHORIZATION_ERROR(ErrorStatus.FORBIDDEN, LegacyErrorCode.E1001, "권한이 없습니다."),

    //회원
    MEMBER_DUPLICATE_EMAIL(ErrorStatus.CONFLICT, LegacyErrorCode.E2000, "중복된 이메일은 불가능합니다."),
    MEMBER_NOT_MATCH_PASSWORD(ErrorStatus.UNAUTHORIZED, LegacyErrorCode.E2001, "비밀번호가 일치하지 않습니다."),

    //회차
    PERFORMANCE_IS_NOT_VALID(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E3000, "유효하지 않은 공연 정보입니다."),
    PERFORMANCE_IS_PAST(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E3001, "과거 공연은 예매할 수 없습니다."),
    NOT_YET_RESERVE_TIME(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E3002, "아직 예매가 오픈되지 않았습니다."),
    NOT_EXIST_AVAILABLE_SEAT(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E3003, "이용 가능한 좌석이 없습니다."),

    //좌석
    SEAT_MISMATCH_IN_PERFORMANCE(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E4000, "요청한 좌석 정보와 일치하지 않습니다."),
    SEAT_ALREADY_SELECTED(ErrorStatus.CONFLICT, LegacyErrorCode.E4001, "이미 선택된 좌석입니다."),
    SEAT_NOT_OWNED(ErrorStatus.FORBIDDEN, LegacyErrorCode.E4002, "본인이 선택한 좌석만 해제할 수 있습니다."),

    //주문
    EXCEED_AVAILABLE_SEATS(ErrorStatus.CONFLICT, LegacyErrorCode.E5000, "총 예매 가능 좌석을 초과하였습니다."),
    SEAT_COUNT_MISMATCH(ErrorStatus.CONFLICT, LegacyErrorCode.E5001, "요청한 좌석 중 일부가 예약 불가능합니다."),
    ORDER_NOT_PENDING(ErrorStatus.CONFLICT, LegacyErrorCode.E5002, "결제 대기 주문만 처리할 수 있습니다."),
    ORDER_NOT_OWNED(ErrorStatus.FORBIDDEN, LegacyErrorCode.E5003, "본인 주문만 처리할 수 있습니다."),
    PENDING_ORDER_ALREADY_EXISTS(ErrorStatus.CONFLICT, LegacyErrorCode.E5004, "이미 진행 중인 결제 대기 주문이 있습니다."),
    ORDER_HOLD_EXPIRED(ErrorStatus.CONFLICT, LegacyErrorCode.E5005, "홀드가 만료된 주문입니다."),

    //선점
    SEAT_ALREADY_HOLD(ErrorStatus.CONFLICT, LegacyErrorCode.E6000, "좌석이 이미 선점되었습니다."),
    EXCEED_HOLD_LIMIT(ErrorStatus.CONFLICT, LegacyErrorCode.E6001, "선점 가능한 좌석 수를 초과하였습니다."),
    HOLD_NOT_FOUND(ErrorStatus.NOT_FOUND, LegacyErrorCode.E6002, "유효한 선점 정보를 찾을 수 없습니다."),
    HOLD_BUSY(ErrorStatus.CONFLICT, LegacyErrorCode.E6003, "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요."),
    HOLD_PROCESSING_FAILED(ErrorStatus.INTERNAL_SERVER_ERROR, LegacyErrorCode.E6004, "좌석 선점 처리 중 오류가 발생했습니다."),

    //공연
    NOT_SUPPORT_SHOW_SORT(ErrorStatus.BAD_REQUEST, LegacyErrorCode.E7000, "지원하지 않는 정렬 조건입니다."),
    SHOW_LIKE_ALREADY_EXISTS(ErrorStatus.CONFLICT, LegacyErrorCode.E7001, "이미 찜한 공연입니다."),

    //대기열 입장
    ADMISSION_TOKEN_REQUIRED(ErrorStatus.FORBIDDEN, LegacyErrorCode.E8000, "대기열 입장 토큰이 필요합니다."),
    ADMISSION_TOKEN_EXPIRED(ErrorStatus.FORBIDDEN, LegacyErrorCode.E8001, "대기열 입장 토큰이 만료되었습니다."),
    ADMISSION_TOKEN_INVALID(ErrorStatus.FORBIDDEN, LegacyErrorCode.E8002, "대기열 입장 토큰이 올바르지 않습니다."),
    ;

    private final ErrorStatus status;
    private final LegacyErrorCode errorCode;
    private final String message;

    ErrorType(final ErrorStatus status, final LegacyErrorCode errorCode, final String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getDescription() {
        return message;
    }

}
