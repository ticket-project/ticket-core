package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;

import java.util.List;

public interface SeatAvailabilityReadRepository {

    /**
     * 회차의 PerformanceSeat 판매 상태만 조회한다(booking local). 등급·가격은 catalog
     * {@code ShowLookup#getSeatMap}에서 따로 조합한다 — booking에서 catalog seat 테이블을
     * 직접 join하지 않는다.
     */
    List<PerformanceSeatStateRow> findSeatStates(Long performanceId);

    record PerformanceSeatStateRow(Long seatId, PerformanceSeatState state) {
    }
}
