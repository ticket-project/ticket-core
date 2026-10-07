package com.ticket.security.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/**
 * 인증(E1000: 인증되지 않았거나 자격 증명이 유효하지 않다)·인가(E1001: 인증은 됐지만 권한이 없다) 오류다. 공개 문구는 {@link SecurityErrorCode}가 갖고 상세 정보는
 * {@code error.data}에 담는다. HTTP 상태는 handler가 오류 코드로 정한다(SecurityExceptionHandler).
 *
 * <p>이름이 {@code SecurityException}이 아닌 이유: {@code java.lang.SecurityException}을 가린다.
 */
public final class AuthException extends TicketException {
    public AuthException(final SecurityErrorCode errorCode) {
        this(errorCode, null);
    }

    /** @param data 왜 실패했는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로 나가고 고정 문구를 덮지 않는다. */
    public AuthException(final SecurityErrorCode errorCode, final @Nullable Object data) {
        super(errorCode, errorCode.getMessage(), data);
    }

    @Override
    public SecurityErrorCode getErrorCode() {
        return (SecurityErrorCode) super.getErrorCode();
    }
}
