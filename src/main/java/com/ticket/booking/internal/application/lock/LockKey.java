package com.ticket.booking.internal.application.lock;

import java.util.List;
import java.util.Objects;

/**
 * 잠글 대상을 업무 의미로 표현한 값이다.
 *
 * <p>key 문자열은 담지 않는다. 저장소 key 형식은 core-infra의 구현이 결정한다.
 */
public record LockKey(LockScope scope, List<String> identifiers) {

    public LockKey {
        Objects.requireNonNull(scope, "scope");
        identifiers = List.copyOf(Objects.requireNonNull(identifiers, "identifiers"));
    }

    public static LockKey seat(final Long performanceId, final Long seatId) {
        return new LockKey(LockScope.SEAT, List.of(String.valueOf(performanceId), String.valueOf(seatId)));
    }

    public static List<LockKey> seats(final Long performanceId, final List<Long> seatIds) {
        return seatIds.stream().map(seatId -> seat(performanceId, seatId)).toList();
    }

    public static LockKey orderStart(final Long memberId, final Long performanceId) {
        return new LockKey(LockScope.ORDER_START, List.of(String.valueOf(memberId), String.valueOf(performanceId)));
    }

    public static LockKey holdCreationOutboxEntry(final Long outboxId) {
        return new LockKey(LockScope.HOLD_CREATION_OUTBOX_ENTRY, List.of(String.valueOf(outboxId)));
    }

    public static LockKey holdReleaseOutboxEntry(final Long outboxId) {
        return new LockKey(LockScope.HOLD_RELEASE_OUTBOX_ENTRY, List.of(String.valueOf(outboxId)));
    }

    public static LockKey holdCreationOutboxBatch() {
        return new LockKey(LockScope.HOLD_CREATION_OUTBOX_BATCH, List.of());
    }

    public static LockKey holdReleaseOutboxBatch() {
        return new LockKey(LockScope.HOLD_RELEASE_OUTBOX_BATCH, List.of());
    }
}
