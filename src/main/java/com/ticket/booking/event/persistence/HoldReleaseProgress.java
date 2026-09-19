package com.ticket.booking.event.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import com.ticket.shared.jpa.AuditedEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * hold 해제 이벤트 listener의 진행 상태를 기록하는 idempotency marker다.
 *
 * <p>{@code eventId}가 기본키라서 같은 event가 재전달돼도 이 row는 하나만 존재한다. {@link #releasedAt}이 있으면 Redis 해제를 이미
 * 수행했다는 뜻이고, listener는 재시도에서 Redis 해제를 반복하지 않는다.
 *
 * <p>기본키를 애플리케이션이 정하므로 Spring Data는 이 entity를 "이미 있는 것"으로 보고 {@code merge}를 시도한다. 그러면 저장할 때마다
 * SELECT가 한 번 더 나가고, 중복 충돌은 {@code save} 호출이 아니라 flush 시점에야 드러난다. {@link Persistable}로 항상 새 row임을
 * 알려 {@code persist}를 타게 한다 — 이 row는 만들어진 뒤 갱신되지 않는다.
 */
@Getter
@Entity
@Table(name = "ORDER_HOLD_RELEASE_PROGRESS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldReleaseProgress extends AuditedEntity implements Persistable<UUID> {
    @Id private UUID eventId;

    @Column(nullable = false)
    private LocalDateTime releasedAt;

    public HoldReleaseProgress(final UUID eventId, final LocalDateTime releasedAt) {
        this.eventId = eventId;
        this.releasedAt = releasedAt;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
