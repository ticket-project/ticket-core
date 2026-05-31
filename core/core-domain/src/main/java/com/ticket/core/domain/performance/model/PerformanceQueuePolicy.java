package com.ticket.core.domain.performance.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "PERFORMANCE_QUEUE_POLICIES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceQueuePolicy extends BaseEntity {

    @Id
    @Column(name = "performance_id")
    private Long performanceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QueueMode queueMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QueueLevel queueLevel;

    @Column
    private Integer maxActiveUsers;

    @Column
    private Integer admitLimitPerTick;

    @Column
    private Integer entryTokenTtlSeconds;

    @Column
    private Integer sessionTtlSeconds;

    @Column
    private LocalDateTime preopenQueueStartAt;

    @Column(length = 255)
    private String waitingRoomMessage;

    @Column(length = 255)
    private String reason;

    private PerformanceQueuePolicy(
            final Performance performance,
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final Integer maxActiveUsers,
            final Integer admitLimitPerTick,
            final Integer entryTokenTtlSeconds,
            final Integer sessionTtlSeconds,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        if (performance == null) {
            throw new IllegalArgumentException("performance must not be null");
        }
        this.performance = performance;
        update(
                queueMode,
                queueLevel,
                maxActiveUsers,
                admitLimitPerTick,
                entryTokenTtlSeconds,
                sessionTtlSeconds,
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    public static PerformanceQueuePolicy create(
            final Performance performance,
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final Integer maxActiveUsers,
            final Integer admitLimitPerTick,
            final Integer entryTokenTtlSeconds,
            final Integer sessionTtlSeconds,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        return new PerformanceQueuePolicy(
                performance,
                queueMode,
                queueLevel,
                maxActiveUsers,
                admitLimitPerTick,
                entryTokenTtlSeconds,
                sessionTtlSeconds,
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    public void update(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final Integer maxActiveUsers,
            final Integer admitLimitPerTick,
            final Integer entryTokenTtlSeconds,
            final Integer sessionTtlSeconds,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        this.queueMode = queueMode;
        this.queueLevel = queueLevel;
        this.maxActiveUsers = maxActiveUsers;
        this.admitLimitPerTick = admitLimitPerTick;
        this.entryTokenTtlSeconds = entryTokenTtlSeconds;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.preopenQueueStartAt = preopenQueueStartAt;
        this.waitingRoomMessage = waitingRoomMessage;
        this.reason = reason;
    }

    public boolean requiresQueueAt(final LocalDateTime now, final LocalDateTime orderCloseTime) {
        if (queueMode == null || queueMode == QueueMode.FORCE_OFF) {
            return false;
        }
        if (queueMode == QueueMode.FORCE_ON) {
            return true;
        }
        if (preopenQueueStartAt == null || now == null || now.isBefore(preopenQueueStartAt)) {
            return false;
        }
        return orderCloseTime == null || !now.isAfter(orderCloseTime);
    }
}
