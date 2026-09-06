package com.ticket.booking.internal.exception;

import com.ticket.error.ErrorCode;

/**
 * admission token 검증 오류 코드다(원래 별도 admission module이 소유했다).
 *
 * <p>코드 값은 외부 계약이라 module 경계가 바뀌어도 재번호하지 않는다. E8xxx 대역이 admission의
 * 것이다.
 */
public enum AdmissionErrorCode implements ErrorCode {

    E8000("대기열 입장 토큰 필요"),
    E8001("대기열 입장 토큰 만료"),
    E8002("대기열 입장 토큰 오류");

    private final String description;

    AdmissionErrorCode(final String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
