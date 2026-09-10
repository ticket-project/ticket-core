package com.ticket.member.exception;


/**
 * 이미 사용 중인 이메일로 가입이나 소셜 연동을 시도했다.
 */
public class DuplicateEmailException extends MemberException {

    private static final String MESSAGE = "중복된 이메일은 불가능합니다.";

    public DuplicateEmailException() {
        this(null);
    }

    public DuplicateEmailException(final Object data) {
        super(MemberErrorCode.E2000, MESSAGE, data);
    }
}
