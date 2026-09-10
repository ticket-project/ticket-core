package com.ticket.booking.infrastructure;

import com.ticket.booking.application.LockKey;
import com.ticket.booking.application.LockManager;
import com.ticket.booking.application.LockOptions;
import com.ticket.booking.exception.HoldBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link LockManager}의 Redisson 구현이다.
 *
 * <p>여러 key를 잠글 때는 정렬한 뒤 multi lock으로 한 번에 잡아 데드락을 피한다.
 * 획득에 실패하면 {@code action}을 실행하지 않고 예외를 던진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockManager implements LockManager {

    private final RedissonClient redissonClient;
    private final RedissonLockKeyFormatter keyFormatter;

    @Override
    public <T> T withLock(final List<LockKey> keys, final LockOptions options, final Supplier<T> action) {
        final List<String> lockNames = keys.stream()
                .map(keyFormatter::format)
                .distinct()
                .sorted()
                .toList();
        if (lockNames.isEmpty()) {
            throw new IllegalArgumentException("잠글 대상이 없습니다.");
        }

        final RLock lock = generateLock(lockNames);
        try {
            if (!tryLock(lock, options)) {
                logLockFailure(options, lockNames);
                throw new HoldBusyException(resolveMessage(options));
            }
            return action.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("분산 락 대기가 중단되었습니다. reason=lock_wait_interrupted keys={}", lockNames, e);
            throw new HoldBusyException(resolveMessage(options));
        } finally {
            unlockQuietly(lock, lockNames);
        }
    }

    private boolean tryLock(final RLock lock, final LockOptions options) throws InterruptedException {
        if (options.autoExtends()) {
            return lock.tryLock(options.waitTime().toMillis(), TimeUnit.MILLISECONDS);
        }
        return lock.tryLock(
                options.waitTime().toMillis(),
                options.leaseTime().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private String resolveMessage(final LockOptions options) {
        return options.failureMessage() == null || options.failureMessage().isBlank()
                ? HoldBusyException.MESSAGE
                : options.failureMessage();
    }

    private void logLockFailure(final LockOptions options, final List<String> lockNames) {
        if (options.warnOnFailure()) {
            log.warn("분산 락 획득에 실패했습니다. reason=lock_not_acquired keys={}", lockNames);
            return;
        }
        log.debug("분산 락 경합으로 실행을 건너뜁니다. reason=lock_not_acquired keys={}", lockNames);
    }

    private RLock generateLock(final List<String> lockNames) {
        if (lockNames.size() == 1) {
            return redissonClient.getLock(lockNames.getFirst());
        }
        final RLock[] locks = lockNames.stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        return redissonClient.getMultiLock(locks);
    }

    private void unlockQuietly(final RLock lock, final List<String> lockNames) {
        try {
            lock.unlock();
        } catch (final IllegalMonitorStateException e) {
            log.debug("분산 락 해제 시점에 현재 스레드가 락을 소유하고 있지 않습니다. keys={}", lockNames, e);
        } catch (final RuntimeException e) {
            log.error("분산 락 해제 중 오류가 발생했습니다. keys={}", lockNames, e);
        }
    }
}
