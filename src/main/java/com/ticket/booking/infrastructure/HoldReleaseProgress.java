package com.ticket.booking.infrastructure;

import com.ticket.booking.support.domain.BookingAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * hold 해제 이벤트 listener의 진행 상태를 기록하는 idempotency marker다.
 *
 * <p>{@code eventId}가 기본키라서 같은 event가 재전달돼도 이 row는 하나만 존재한다.
 * {@link #releasedAt}이 있으면 Redis 해제를 이미 수행했다는 뜻이고, listener는 재시도에서
 * Redis 해제를 반복하지 않는다.
 */
@Getter
@Entity
@Table(name = "ORDER_HOLD_RELEASE_PROGRESS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldReleaseProgress extends BookingAuditedEntity {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private LocalDateTime releasedAt;

    public HoldReleaseProgress(final UUID eventId, final LocalDateTime releasedAt) {
        this.eventId = eventId;
        this.releasedAt = releasedAt;
    }
}
