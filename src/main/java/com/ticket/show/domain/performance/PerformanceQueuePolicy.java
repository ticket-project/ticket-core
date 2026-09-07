package com.ticket.show.domain.performance;

import com.ticket.show.domain.ShowAuditedEntity;
import com.ticket.show.domain.performance.QueueActivation;
import com.ticket.show.domain.queue.QueueLevel;
import com.ticket.show.domain.queue.QueueMode;
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
public class PerformanceQueuePolicy extends ShowAuditedEntity {

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
    private LocalDateTime preopenQueueStartAt;

    @Column(length = 255)
    private String waitingRoomMessage;

    @Column(length = 255)
    private String reason;

    private PerformanceQueuePolicy(
            final Performance performance,
            final QueueMode queueMode,
            final QueueLevel queueLevel,
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
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    public static PerformanceQueuePolicy create(
            final Performance performance,
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        return new PerformanceQueuePolicy(
                performance,
                queueMode,
                queueLevel,
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    public void update(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        this.queueMode = queueMode;
        this.queueLevel = queueLevel;
        this.preopenQueueStartAt = preopenQueueStartAt;
        this.waitingRoomMessage = waitingRoomMessage;
        this.reason = reason;
    }

    public boolean requiresQueueAt(final LocalDateTime now, final LocalDateTime orderCloseTime) {
        return QueueActivation.isRequiredAt(queueMode, preopenQueueStartAt, now, orderCloseTime);
    }
}
