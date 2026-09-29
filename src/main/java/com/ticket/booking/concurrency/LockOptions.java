package com.ticket.booking.concurrency;

import java.time.Duration;

/**
 * 락 획득 방식이다. 대기 시간은 기술 설정이므로 호출부가 의도를 담아 지정한다. 잡은 락은 실행이 끝날 때까지 자동 연장된다.
 *
 * @param waitTime 락을 기다리는 시간
 * @param failureMessage 획득 실패 시 사용할 메시지. 비어 있으면 기본 메시지를 쓴다
 */
public record LockOptions(Duration waitTime, String failureMessage) {
    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(5);

    public static LockOptions defaults() {
        return new LockOptions(DEFAULT_WAIT_TIME, "");
    }

    public static LockOptions waiting(final Duration waitTime) {
        return new LockOptions(waitTime, "");
    }

    public LockOptions withFailureMessage(final String message) {
        return new LockOptions(waitTime, message);
    }
}
