package com.ticket.member.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증되지 않았거나 자격 증명이 유효하지 않다.
 *
 * <p>공개 메시지는 항상 고정 문구이고, 왜 실패했는지("만료된 리프레시 토큰" 등)는 {@code data}에
 * 실린다 — 기존 응답 계약이 그렇다. 다만 <b>자격 증명이 틀렸다는 사실 이상을 알려주지 않는다</b>:
 * 존재하지 않는 계정과 비밀번호 불일치를 구분하는 문구를 넣지 않는다.
 */
public class UnauthenticatedException extends MemberException {

    /** filter chain의 entry point도 같은 문구를 써야 해서 노출한다. */
    public static final String MESSAGE = "로그인이 필요합니다.";

    public UnauthenticatedException() {
        this(null);
    }

    public UnauthenticatedException(final Object data) {
        super(HttpStatus.UNAUTHORIZED, MemberErrorCode.E1000, MESSAGE, data);
    }
}
