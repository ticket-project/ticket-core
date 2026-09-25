package com.ticket.member.exception;

import com.ticket.shared.exception.ErrorCode;

/** member module이 소유하는 회원 오류 코드다. */
public enum MemberErrorCode implements ErrorCode {
    E2000("중복 이메일");
    private final String description;

    MemberErrorCode(final String description) {
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
