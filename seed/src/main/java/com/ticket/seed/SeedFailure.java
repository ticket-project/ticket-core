package com.ticket.seed;

/**
 * 시드 실행을 중단시키는 실패다. 메시지에 원인과 필요한 조치를 함께 담는다 — 이 프로그램의
 * 사용자는 stack trace가 아니라 "무엇을 해야 하는가"를 읽는다.
 */
class SeedFailure extends RuntimeException {

    SeedFailure(final String message) {
        super(message);
    }

    SeedFailure(final String message, final Throwable cause) {
        super(message, cause);
    }
}
