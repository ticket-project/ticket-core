package com.ticket.catalog.internal.application.performance.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * {@link com.ticket.catalog.BookingPolicyLookup}이 가격 계산용으로 조회하는 좌석별 등급 가격 포트다.
 * 회차가 속한 show의 좌석 등급 가격만 조회하고 판매 상태(PerformanceSeat)는 다루지 않는다.
 */
public interface BookingSeatPriceReadRepository {

    /**
     * 빈 {@code seatIds}는 빈 map을 반환한다. 회차 또는 seat가 없거나 그 show에 속하지 않으면
     * 해당 seatId는 결과에서 빠진다.
     */
    Map<Long, BigDecimal> findSeatPrices(long performanceId, List<Long> seatIds);
}
