package com.ticket.member.exception;


/**
 * 인증은 됐지만 그 자원에 대한 권한이 없다.
 */
public class AuthorizationException extends MemberException {

    private static final String MESSAGE = "권한이 없습니다.";

    public AuthorizationException() {
        this(null);
    }

    /**
     * @param detail 어떤 권한이 없는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로
     *               나가고 고정 {@code MESSAGE}를 덮지 않는다.
     */
    public AuthorizationException(final String detail) {
        super(MemberErrorCode.E1001, MESSAGE, detail);
    }
}
