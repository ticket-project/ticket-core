package com.ticket.booking.internal.application.lock;

import java.util.List;
import java.util.function.Supplier;

/**
 * 여러 인스턴스에 걸친 상호 배제를 제공하는 포트다.
 *
 * <p>락을 먼저 잡고 그 안에서 트랜잭션을 시작한다. 트랜잭션 커밋이 끝난 뒤에 락이 풀린다.
 * 획득에 실패하면 예외를 던지고 {@code action}은 실행하지 않는다.
 */
public interface LockManager {

    <T> T withLock(List<LockKey> keys, LockOptions options, Supplier<T> action);

    default void withLock(final List<LockKey> keys, final LockOptions options, final Runnable action) {
        withLock(keys, options, () -> {
            action.run();
            return null;
        });
    }
}
