package com.ticket.booking.concurrency;

/**
 * 락이 보호하는 대상의 업무 의미다.
 *
 * <p>실제 Redis key 형식은 하위 package {@code redis}의 {@code RedissonLockKeyFormatter}가 정한다. application 계층은 무엇을 잠그는지만 말한다.
 */
public enum LockScope {
    /** 회차의 좌석 한 자리. 동시 점유를 막는다. */
    SEAT
}
