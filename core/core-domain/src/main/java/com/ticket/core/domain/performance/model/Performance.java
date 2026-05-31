package com.ticket.core.domain.performance.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "PERFORMANCES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Show show;

    private Long performanceNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime orderOpenTime;

    private LocalDateTime orderCloseTime;

    private Integer maxCanHoldCount;

    @Column
    private Integer holdTime = 600;

    @OneToOne(mappedBy = "performance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PerformanceQueuePolicy queuePolicy;

    public Performance(
            final Show show,
            final Long performanceNo,
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final Integer maxCanHoldCount,
            final Integer holdTime
    ) {
        this.show = show;
        this.performanceNo = performanceNo;
        this.startTime = startTime;
        this.endTime = endTime;
        this.orderOpenTime = orderOpenTime;
        this.orderCloseTime = orderCloseTime;
        this.maxCanHoldCount = validateMaxCanHoldCount(maxCanHoldCount);
        this.holdTime = holdTime;
    }

    public boolean isOverCount(final long requestReserveCount) {
        if (maxCanHoldCount == null) {
            return false;
        }
        return requestReserveCount > maxCanHoldCount;
    }

    public boolean isBookingOpen(final LocalDateTime now) {
        if (orderOpenTime == null || orderCloseTime == null) {
            return false;
        }
        if (now.isBefore(orderOpenTime)) {
            return false;
        }
        return !now.isAfter(orderCloseTime);
    }

    public boolean requiresQueueAt(final LocalDateTime now) {
        if (queuePolicy == null) {
            return false;
        }
        return queuePolicy.requiresQueueAt(now, orderCloseTime);
    }

    public void updateQueuePolicy(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final Integer maxActiveUsers,
            final Integer entryTokenTtlSeconds,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        updateQueuePolicy(
                queueMode,
                queueLevel,
                maxActiveUsers,
                null,
                entryTokenTtlSeconds,
                null,
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    public void updateQueuePolicy(
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
        if (queuePolicy == null) {
            queuePolicy = PerformanceQueuePolicy.create(
                    this,
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
            return;
        }
        queuePolicy.update(
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

    public QueueMode getQueueMode() {
        return queuePolicy == null ? null : queuePolicy.getQueueMode();
    }

    public QueueLevel getQueueLevel() {
        return queuePolicy == null ? null : queuePolicy.getQueueLevel();
    }

    public Integer getMaxActiveUsers() {
        return queuePolicy == null ? null : queuePolicy.getMaxActiveUsers();
    }

    public Integer getEntryTokenTtlSeconds() {
        return queuePolicy == null ? null : queuePolicy.getEntryTokenTtlSeconds();
    }

    public LocalDateTime getPreopenQueueStartAt() {
        return queuePolicy == null ? null : queuePolicy.getPreopenQueueStartAt();
    }

    public String getWaitingRoomMessage() {
        return queuePolicy == null ? null : queuePolicy.getWaitingRoomMessage();
    }

    public String getReason() {
        return queuePolicy == null ? null : queuePolicy.getReason();
    }

    private Integer validateMaxCanHoldCount(final Integer maxCanHoldCount) {
        if (maxCanHoldCount == null) {
            return null;
        }
        if (maxCanHoldCount >= 2) {
            return maxCanHoldCount;
        }
        throw new CoreException(ErrorType.INVALID_REQUEST, "maxCanHoldCount는 2 이상 또는 null 이어야 합니다.");
    }
}
