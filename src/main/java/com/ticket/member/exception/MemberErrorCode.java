package com.ticket.member.exception;

import com.ticket.shared.exception.ErrorCode;

/** member module이 소유하는 회원 오류 코드다. */
public enum MemberErrorCode implements ErrorCode {
    E2000("중복 이메일", "중복된 이메일은 불가능합니다.");
    private final String description;
    private final String message;

    MemberErrorCode(final String description, final String message) {
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
