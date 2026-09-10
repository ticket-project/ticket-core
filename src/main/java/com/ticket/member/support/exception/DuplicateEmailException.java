package com.ticket.member.support.exception;


/**
 * 이미 사용 중인 이메일로 가입이나 소셜 연동을 시도했다.
 */
public class DuplicateEmailException extends MemberException {

    private static final String MESSAGE = "중복된 이메일은 불가능합니다.";

    public DuplicateEmailException() {
        this(null);
    }

    /**
     * @param detail 어느 경로에서 중복됐는지 좁히는 <b>공개</b> 상세 문구다. 그대로
     *               {@code error.data}로 나가고 고정 {@code MESSAGE}를 덮지 않는다.
     */
    public DuplicateEmailException(final String detail) {
        super(MemberErrorCode.E2000, MESSAGE, detail);
    }
}
