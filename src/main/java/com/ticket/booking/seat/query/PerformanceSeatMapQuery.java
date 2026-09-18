package com.ticket.booking.seat.query;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 회차 정적 seat-map에 필요한 booking local 판매 편성(PerformanceSeat) 조회다. 이 회차에 실제로 판매 편성된 좌석만 반환한다 — 편성되지 않은 물리
 * Seat는 여기 나타나지 않으므로 seat-map 응답에서도 자연히 제외된다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatMapQuery {
    private final JPAQueryFactory queryFactory;

    public List<PerformanceSeatMapRow> findAllByPerformanceId(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceSeatMapRow.class,
                                performanceSeat.id,
                                performanceSeat.seatId,
                                performanceSeat.performanceGradeId,
                                performanceSeat.unitPrice))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .fetch();
    }

    public record PerformanceSeatMapRow(
            Long performanceSeatId, Long seatId, Long performanceGradeId, BigDecimal unitPrice) {}
}
