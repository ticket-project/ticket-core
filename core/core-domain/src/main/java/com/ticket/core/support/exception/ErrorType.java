package com.ticket.core.support.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "알 수 없는 에러입니다."),
    NOT_FOUND_DATA(HttpStatus.NOT_FOUND, ErrorCode.E404, "요청하신 정보를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청이 올바르지 않습니다."),

    //AUTH
    AUTHENTICATION_ERROR(HttpStatus.UNAUTHORIZED, ErrorCode.E1000, "로그인이 필요합니다."),
    AUTHORIZATION_ERROR(HttpStatus.FORBIDDEN, ErrorCode.E1001, "권한이 없습니다."),

    //회원
    MEMBER_DUPLICATE_EMAIL(HttpStatus.CONFLICT, ErrorCode.E2000, "중복된 이메일은 불가능합니다."),
    MEMBER_NOT_MATCH_PASSWORD(HttpStatus.UNAUTHORIZED, ErrorCode.E2001, "비밀번호가 일치하지 않습니다."),

    //회차
    PERFORMANCE_IS_NOT_VALID(HttpStatus.BAD_REQUEST, ErrorCode.E3000, "유효하지 않은 공연 정보입니다."),
    PERFORMANCE_IS_PAST(HttpStatus.BAD_REQUEST, ErrorCode.E3001, "과거 공연은 예매할 수 없습니다."),
    NOT_YET_RESERVE_TIME(HttpStatus.BAD_REQUEST, ErrorCode.E3002, "아직 예매가 오픈되지 않았습니다."),
    NOT_EXIST_AVAILABLE_SEAT(HttpStatus.BAD_REQUEST, ErrorCode.E3003, "이용 가능한 좌석이 없습니다."),

    //좌석
    SEAT_MISMATCH_IN_PERFORMANCE(HttpStatus.BAD_REQUEST, ErrorCode.E4000, "요청한 좌석 정보와 일치하지 않습니다."),
    SEAT_ALREADY_SELECTED(HttpStatus.CONFLICT, ErrorCode.E4001, "이미 선택된 좌석입니다."),
    SEAT_NOT_OWNED(HttpStatus.FORBIDDEN, ErrorCode.E4002, "본인이 선택한 좌석만 해제할 수 있습니다."),

    //주문
    EXCEED_AVAILABLE_SEATS(HttpStatus.CONFLICT, ErrorCode.E5000, "총 예매 가능 좌석을 초과하였습니다."),
    SEAT_COUNT_MISMATCH(HttpStatus.CONFLICT, ErrorCode.E5001, "요청한 좌석 중 일부가 예약 불가능합니다."),
    ORDER_NOT_PENDING(HttpStatus.CONFLICT, ErrorCode.E5002, "결제 대기 주문만 처리할 수 있습니다."),
    ORDER_NOT_OWNED(HttpStatus.FORBIDDEN, ErrorCode.E5003, "본인 주문만 처리할 수 있습니다."),
    PENDING_ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCode.E5004, "이미 진행 중인 결제 대기 주문이 있습니다."),
    ORDER_HOLD_EXPIRED(HttpStatus.CONFLICT, ErrorCode.E5005, "홀드가 만료된 주문입니다."),

    //선점
    SEAT_ALREADY_HOLD(HttpStatus.CONFLICT, ErrorCode.E6000, "좌석이 이미 선점되었습니다."),
    EXCEED_HOLD_LIMIT(HttpStatus.CONFLICT, ErrorCode.E6001, "선점 가능한 좌석 수를 초과하였습니다."),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E6002, "유효한 선점 정보를 찾을 수 없습니다."),
    HOLD_BUSY(HttpStatus.CONFLICT, ErrorCode.E6003, "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요."),
    HOLD_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E6004, "좌석 선점 처리 중 오류가 발생했습니다."),

    //공연
    NOT_SUPPORT_SHOW_SORT(HttpStatus.BAD_REQUEST, ErrorCode.E7000, "지원하지 않는 정렬 조건입니다."),
    SHOW_LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCode.E7001, "이미 찜한 공연입니다."),

    //대기열 입장
    ADMISSION_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, ErrorCode.E8000, "대기열 입장 토큰이 필요합니다."),
    ADMISSION_TOKEN_EXPIRED(HttpStatus.FORBIDDEN, ErrorCode.E8001, "대기열 입장 토큰이 만료되었습니다."),
    ADMISSION_TOKEN_INVALID(HttpStatus.FORBIDDEN, ErrorCode.E8002, "대기열 입장 토큰이 올바르지 않습니다."),
    ;

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final String message;

    ErrorType(final HttpStatus status, final ErrorCode errorCode, final String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getDescription() {
        return message;
    }

}
