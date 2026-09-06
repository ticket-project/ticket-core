package com.ticket.booking.application.lock;

import java.time.Duration;

/**
 * 락 획득 방식이다. 대기 시간과 임대 시간은 기술 설정이므로 호출부가 의도를 담아 지정한다.
 *
 * @param waitTime       락을 기다리는 시간
 * @param leaseTime      락 임대 시간. {@code null}이면 실행 중인 락을 자동 연장한다
 * @param warnOnFailure  획득 실패를 경고로 남길지 여부. 정상적인 경합이면 false를 쓴다
 * @param failureMessage 획득 실패 시 사용할 메시지. 비어 있으면 기본 메시지를 쓴다
 */
public record LockOptions(
        Duration waitTime,
        Duration leaseTime,
        boolean warnOnFailure,
        String failureMessage
) {

    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(5);

    public static LockOptions defaults() {
        return new LockOptions(DEFAULT_WAIT_TIME, null, true, "");
    }

    public static LockOptions waiting(final Duration waitTime) {
        return new LockOptions(waitTime, null, true, "");
    }

    public LockOptions withLeaseTime(final Duration newLeaseTime) {
        return new LockOptions(waitTime, newLeaseTime, warnOnFailure, failureMessage);
    }

    public LockOptions withoutWarningOnFailure() {
        return new LockOptions(waitTime, leaseTime, false, failureMessage);
    }

    public LockOptions withFailureMessage(final String message) {
        return new LockOptions(waitTime, leaseTime, warnOnFailure, message);
    }

    public boolean autoExtends() {
        return leaseTime == null;
    }
}
