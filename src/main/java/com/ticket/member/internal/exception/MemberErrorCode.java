package com.ticket.member.internal.exception;

import com.ticket.error.ErrorCode;

/**
 * member module이 소유하는 오류 코드다. E1xxx는 인증·인가, E2xxx는 회원이다.
 */
public enum MemberErrorCode implements ErrorCode {

    E1000("인증 오류"),
    E1001("인가 오류"),
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
