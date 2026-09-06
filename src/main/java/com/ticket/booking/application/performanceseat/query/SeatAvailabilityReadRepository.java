package com.ticket.booking.application.performanceseat.query;

import com.ticket.booking.domain.performanceseat.model.PerformanceSeatState;

import java.util.List;

public interface SeatAvailabilityReadRepository {

    /**
     * 회차의 PerformanceSeat 판매 상태와 배정된 PerformanceGrade ID만 조회한다(booking local).
     * 등급 코드/이름/표시순서는 catalog {@code PerformanceSaleCatalog}에서 performanceGradeId로
     * 따로 조합한다 — booking에서 catalog grade 테이블을 직접 join하지 않는다.
     */
    List<PerformanceSeatStateRow> findSeatStates(Long performanceId);

    record PerformanceSeatStateRow(Long seatId, PerformanceSeatState state, Long performanceGradeId) {
    }
}
