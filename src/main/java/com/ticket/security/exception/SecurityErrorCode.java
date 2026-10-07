package com.ticket.security.exception;

import com.ticket.shared.exception.ErrorCode;

/** 인증·인가 오류의 기존 외부 코드다. */
public enum SecurityErrorCode implements ErrorCode {
    E1000("인증 오류", "로그인이 필요합니다."),
    E1001("인가 오류", "권한이 없습니다.");
    private final String description;
    private final String message;

    SecurityErrorCode(final String description, final String message) {
        this.description = description;
        this.message = message;
    }

    /** 응답 {@code error.message}로 나가는 공개 문구다. 외부 계약이라 바꾸지 않는다. */
    public String getMessage() {
        return message;
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
