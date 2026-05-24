package com.ticket.core.domain.performance.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QueueMode queueMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QueueLevel queueLevel;

    @Column
    private Integer maxActiveUsers;

    @Column
    private Integer entryTokenTtlSeconds;

    @Column
    private LocalDateTime preopenQueueStartAt;

    @Column(length = 255)
    private String waitingRoomMessage;

    @Column(length = 255)
    private String reason;

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
    public void updateQueuePolicy(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final Integer maxActiveUsers,
            final Integer entryTokenTtlSeconds,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        this.queueMode = queueMode;
        this.queueLevel = queueLevel;
        this.maxActiveUsers = maxActiveUsers;
        this.entryTokenTtlSeconds = entryTokenTtlSeconds;
        this.preopenQueueStartAt = preopenQueueStartAt;
        this.waitingRoomMessage = waitingRoomMessage;
        this.reason = reason;
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
