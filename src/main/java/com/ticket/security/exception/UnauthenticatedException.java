package com.ticket.security.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/** 인증되지 않았거나 자격 증명이 유효하지 않다. 공개 메시지는 고정하고 상세 정보는 error.data에 담는다. */
public final class UnauthenticatedException extends TicketException {
    public static final String MESSAGE = "로그인이 필요합니다.";

    public UnauthenticatedException() {
        this(null);
    }

    public UnauthenticatedException(final @Nullable String detail) {
        super(SecurityErrorCode.E1000, MESSAGE, detail);
    }
}
