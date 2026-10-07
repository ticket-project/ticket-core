package com.ticket.booking.exception;

import com.ticket.shared.exception.ErrorCode;

/**
 * admission token 검증 오류 코드다(원래 별도 admission module이 소유했다).
 *
 * <p>코드 값은 외부 계약이라 module 경계가 바뀌어도 재번호하지 않는다. E8xxx 대역이 admission의 것이다.
 */
public enum AdmissionErrorCode implements ErrorCode {
    E8000("대기열 입장 토큰이 필요합니다."),
    E8001("대기열 입장 토큰이 만료되었습니다."),
    E8002("대기열 입장 토큰이 올바르지 않습니다.");
    private final String message;

    AdmissionErrorCode(final String message) {
        this.message = message;
    }

    /** 응답 {@code error.message}로 나가는 공개 문구다. 검증 실패 사유와 무관하게 코드마다 하나로 고정한다. */
    public String getMessage() {
        return message;
    }
}
