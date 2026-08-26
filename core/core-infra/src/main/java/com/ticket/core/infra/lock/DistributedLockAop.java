package com.ticket.core.infra.lock;

import com.ticket.core.infra.lock.CustomSpringELParser;
import com.ticket.support.error.CoreException;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributedLockAop {

    private static final String LOCK_PREFIX = "LOCK:";
    private static final Logger log = LoggerFactory.getLogger(DistributedLockAop.class);

    private final RedissonClient redissonClient;

    @Around("@annotation(com.ticket.core.support.lock.DistributedLock)")
    public Object around(final ProceedingJoinPoint joinPoint) throws Throwable {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final DistributedLock distributedLock = signature.getMethod().getAnnotation(DistributedLock.class);
        final List<String> keys = CustomSpringELParser.getDynamicValue(
                lockPrefix(distributedLock.prefix()),
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.dynamicKey()
        ).stream().distinct().sorted().toList();
        final RLock lock = generateLock(keys);

        try {
            final boolean available = tryLock(lock, distributedLock);
            if (!available) {
                logLockFailure(distributedLock, keys);
                throw new CoreException(distributedLock.errorType(), resolveMessage(distributedLock));
            }
            return joinPoint.proceed();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logLockInterrupted(distributedLock, keys, e);
            throw new CoreException(distributedLock.errorType(), resolveMessage(distributedLock));
        } finally {
            unlockQuietly(lock, keys);
        }
    }

    private boolean tryLock(final RLock lock, final DistributedLock distributedLock) throws InterruptedException {
        if (distributedLock.leaseTime() < 0L) {
            return lock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());
        }
        return lock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
        );
    }

    private String lockPrefix(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return LOCK_PREFIX;
        }
        return LOCK_PREFIX + prefix + ":";
    }

    private String resolveMessage(final DistributedLock distributedLock) {
        return distributedLock.message().isBlank()
                ? distributedLock.errorType().getMessage()
                : distributedLock.message();
    }

    private void logLockFailure(final DistributedLock distributedLock, final List<String> keys) {
        if (distributedLock.warnOnFailure()) {
            log.warn(
                    "분산 락 획득에 실패했습니다. reason=lock_not_acquired operation={} keys={}",
                    distributedLock.prefix(),
                    keys
            );
            return;
        }
        log.debug(
                "분산 락 경합으로 실행을 건너뜁니다. reason=lock_not_acquired operation={} keys={}",
                distributedLock.prefix(),
                keys
        );
    }

    private void logLockInterrupted(
            final DistributedLock distributedLock,
            final List<String> keys,
            final InterruptedException exception
    ) {
        log.warn(
                "분산 락 대기가 중단되었습니다. reason=lock_wait_interrupted operation={} keys={}",
                distributedLock.prefix(),
                keys,
                exception
        );
    }

    private RLock generateLock(final List<String> keys) {
        if (keys.size() == 1) {
            return redissonClient.getLock(keys.getFirst());
        }
        final RLock[] locks = keys.stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        return redissonClient.getMultiLock(locks);
    }

    private void unlockQuietly(final RLock lock, final List<String> keys) {
        try {
            lock.unlock();
        } catch (final IllegalMonitorStateException e) {
            log.debug("분산 락 해제 시점에 현재 스레드가 락을 소유하고 있지 않습니다. keys={}", keys, e);
        } catch (final RuntimeException e) {
            log.error("분산 락 해제 중 오류가 발생했습니다. keys={}", keys, e);
        }
    }
}
