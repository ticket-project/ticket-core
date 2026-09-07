package com.ticket.show.domain.performance;

import com.ticket.show.domain.ShowAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    /**
     * Show는 Performance와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate 경계를
     * 넘는 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛
     * {@code @ManyToOne Show show} 암묵 매핑과 같은 {@code show_id}를 그대로 써서 스키마가 바뀌지
     * 않는다.
     */
    @Column(name = "show_id")
    private Long showId;

    private Long performanceNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    public Performance(
            final Long showId,
            final Long performanceNo,
            final LocalDateTime startTime,
            final LocalDateTime endTime
    ) {
        this.showId = showId;
        this.performanceNo = performanceNo;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
