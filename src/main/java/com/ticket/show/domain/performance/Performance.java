package com.ticket.show.domain.performance;

import com.ticket.show.domain.ShowAuditedEntity;
import com.ticket.show.domain.show.Show;
import jakarta.persistence.Entity;
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

/**
 * Show의 특정 상영 회차다. 회차 정체성과 일정(startTime/endTime)만 소유한다. 예매 접수 기간·Hold
 * 제한·대기열 진입 정책은 Booking BC의 {@code PerformanceSalesPolicy}가 소유한다(ADR 0006
 * "Performance의 책임 혼재" A2, {@code performanceId} scalar로만 연결되고 cross-module JPA
 * 연관관계·DB FK는 없다).
 */
@Getter
@Entity
@Table(name = "PERFORMANCES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Show show;

    private Long performanceNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    public Performance(
            final Show show,
            final Long performanceNo,
            final LocalDateTime startTime,
            final LocalDateTime endTime
    ) {
        this.show = show;
        this.performanceNo = performanceNo;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
