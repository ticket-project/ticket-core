package com.ticket.booking.seat.query;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.domain.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SeatAvailabilityQuery {
    private final JPAQueryFactory queryFactory;

    /**
     * 회차의 PerformanceSeat 판매 상태와 배정된 PerformanceGrade ID만 조회한다(booking local). 등급 코드/이름/표시순서는 show
     * {@code PerformanceSaleCatalogApi}에서 performanceGradeId로 따로 조합한다 — booking에서 show grade 테이블을 직접
     * join하지 않는다.
     */
    public List<PerformanceSeatStateRow> findSeatStates(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceSeatStateRow.class,
                                performanceSeat.seatId,
                                performanceSeat.state,
                                performanceSeat.performanceGradeId))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch();
    }

    public record PerformanceSeatStateRow(
            Long seatId, PerformanceSeatState state, Long performanceGradeId) {}
}
