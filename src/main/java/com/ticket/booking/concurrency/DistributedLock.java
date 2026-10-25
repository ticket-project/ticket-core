package com.ticket.booking.concurrency;

import java.util.List;
import java.util.function.Supplier;

/**
 * 여러 인스턴스에 걸친 상호 배제를 제공하는 포트다.
 *
 * <p>트랜잭션 경계는 호출자가 정한다. 현재 호출자는 락 안에서 DB 트랜잭션을 열지 않고 Redis·WebSocket 작업만 한다. 획득에 실패하면 예외를 던지고 {@code action}은 실행하지 않는다.
 */
public interface DistributedLock {
    <T> T withLock(List<LockKey> keys, LockOptions options, Supplier<T> action);

    default void withLock(final List<LockKey> keys, final LockOptions options, final Runnable action) {
        withLock(keys, options, () -> {
            action.run();
            return null;
        });
    }
}
