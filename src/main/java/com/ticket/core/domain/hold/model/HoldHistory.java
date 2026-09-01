package com.ticket.core.domain.hold.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.hold.model.HoldHistoryEventType;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "HOLD_HISTORY",
        indexes = {
                @Index(name = "IDX_HOLD_HISTORY_HOLD_KEY", columnList = "hold_key")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String holdKey;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long performanceId;

    @Column(nullable = false)
    private Long performanceSeatId;

    @Column(nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HoldHistoryEventType eventType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private HoldReleaseReason releaseReason;

    private HoldHistory(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final Long performanceSeatId,
            final Long seatId,
            final HoldHistoryEventType eventType,
            final LocalDateTime occurredAt,
            final LocalDateTime expiresAt,
            final HoldReleaseReason releaseReason
    ) {
        this.holdKey = holdKey;
        this.memberId = memberId;
        this.performanceId = performanceId;
        this.performanceSeatId = performanceSeatId;
        this.seatId = seatId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.expiresAt = expiresAt;
        this.releaseReason = releaseReason;
    }

    public static HoldHistory created(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final Long performanceSeatId,
            final Long seatId,
            final LocalDateTime occurredAt,
            final LocalDateTime expiresAt
    ) {
        return new HoldHistory(holdKey, memberId, performanceId, performanceSeatId, seatId,
                HoldHistoryEventType.CREATED, occurredAt, expiresAt, null);
    }

    public static HoldHistory expired(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final Long performanceSeatId,
            final Long seatId,
            final LocalDateTime occurredAt,
            final HoldReleaseReason releaseReason
    ) {
        return new HoldHistory(holdKey, memberId, performanceId, performanceSeatId, seatId,
                HoldHistoryEventType.EXPIRED, occurredAt, null, releaseReason);
    }

    public static HoldHistory canceled(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final Long performanceSeatId,
            final Long seatId,
            final LocalDateTime occurredAt,
            final HoldReleaseReason releaseReason
    ) {
        return new HoldHistory(holdKey, memberId, performanceId, performanceSeatId, seatId,
                HoldHistoryEventType.CANCELED, occurredAt, null, releaseReason);
    }
}
