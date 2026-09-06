package com.ticket.catalog.domain.performance;

import com.ticket.catalog.domain.CatalogAuditedEntity;
import com.ticket.catalog.domain.queue.QueueLevel;
import com.ticket.catalog.domain.queue.QueueMode;
import com.ticket.catalog.domain.show.Show;
import com.ticket.error.InvalidRequestException;
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
public class Performance extends CatalogAuditedEntity {

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

    public boolean requiresQueueAt(final LocalDateTime now) {
        if (queuePolicy == null) {
            return false;
        }
        return queuePolicy.requiresQueueAt(now, orderCloseTime);
    }

    public void updateQueuePolicy(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        if (queuePolicy == null) {
            queuePolicy = PerformanceQueuePolicy.create(
                    this,
                    queueMode,
                    queueLevel,
                    preopenQueueStartAt,
                    waitingRoomMessage,
                    reason
            );
            return;
        }
        queuePolicy.update(
                queueMode,
                queueLevel,
                preopenQueueStartAt,
                waitingRoomMessage,
                reason
        );
    }

    private Integer validateMaxCanHoldCount(final Integer maxCanHoldCount) {
        if (maxCanHoldCount == null) {
            return null;
        }
        if (maxCanHoldCount >= 2) {
            return maxCanHoldCount;
        }
        throw new InvalidRequestException("maxCanHoldCount는 2 이상 또는 null 이어야 합니다.");
    }
}
