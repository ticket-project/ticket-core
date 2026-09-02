package com.ticket.booking.internal.infrastructure.lock;

import com.ticket.booking.internal.application.lock.LockKey;
import org.springframework.stereotype.Component;

/**
 * 업무 의미의 락 대상을 Redis key 문자열로 바꾼다.
 *
 * <p>key 형식은 저장 기술의 관심사이므로 이 모듈에만 둔다. 형식을 바꾸면 배포 중
 * 기존 락과 겹치지 않으므로 전환 절차를 함께 설계해야 한다.
 */
@Component
public class RedissonLockKeyFormatter {

    private static final String LOCK_PREFIX = "LOCK:";

    public String format(final LockKey key) {
        return switch (key.scope()) {
            case SEAT -> LOCK_PREFIX + "hold:" + join(key);
            case ORDER_START -> LOCK_PREFIX + "start-order:" + join(key);
            case HOLD_CREATION_OUTBOX_ENTRY -> LOCK_PREFIX + "hold-creation-outbox-entry:" + join(key);
            case HOLD_RELEASE_OUTBOX_ENTRY -> LOCK_PREFIX + "hold-release-outbox-entry:" + join(key);
            case HOLD_CREATION_OUTBOX_BATCH -> LOCK_PREFIX + "hold-creation-outbox:batch";
            case HOLD_RELEASE_OUTBOX_BATCH -> LOCK_PREFIX + "hold-release-outbox:batch";
        };
    }

    private String join(final LockKey key) {
        return String.join(":", key.identifiers());
    }
}
