package com.ticket.security.exception;

import com.ticket.shared.exception.ErrorCode;

/** 인증·인가 오류의 기존 외부 코드다. */
public enum SecurityErrorCode implements ErrorCode {
    E1000("인증 오류"),
    E1001("인가 오류");

    private final String description;

    SecurityErrorCode(final String description) {
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
