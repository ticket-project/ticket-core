package com.ticket.booking.seat.usecase;

import java.math.BigDecimal;

import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;

/** 조회 결과로 {@link PerformanceSeat} 엔티티를 받는 use case 단위 테스트가 쓰는 fixture다. id는 JPA가 채우는 필드라 생성자 대신 reflection으로 심는다. */
final class PerformanceSeatFixture {
    private PerformanceSeatFixture() {}

    static PerformanceSeat seat(
            final Long performanceSeatId,
            final Long seatId,
            final Long performanceGradeId,
            final PerformanceSeatState state,
            final BigDecimal unitPrice) {
        final PerformanceSeat performanceSeat = new PerformanceSeat(10L, seatId, performanceGradeId, state, unitPrice);
        ReflectionTestUtils.setField(performanceSeat, "id", performanceSeatId);
        return performanceSeat;
    }

    static PerformanceSeat seat(
            final Long performanceSeatId,
            final Long seatId,
            final Long performanceGradeId,
            final PerformanceSeatState state) {
        return seat(performanceSeatId, seatId, performanceGradeId, state, BigDecimal.ZERO);
    }

    static PerformanceSeat seat(
            final Long performanceSeatId,
            final Long seatId,
            final Long performanceGradeId,
            final BigDecimal unitPrice) {
        return seat(performanceSeatId, seatId, performanceGradeId, PerformanceSeatState.AVAILABLE, unitPrice);
    }
}
