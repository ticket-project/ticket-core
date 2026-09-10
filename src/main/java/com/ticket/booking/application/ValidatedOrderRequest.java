package com.ticket.booking.application;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;

import java.util.List;

/**
 * 주문 생성 전 검증 결과. booking local 판매 정책·show 표시 snapshot과 booking local 좌석 조회 결과를
 * 함께 전달해 이후 단계가 같은 값을 다시 조회하지 않게 한다.
 */
public record ValidatedOrderRequest(
        PerformanceSalesPolicy policy,
        List<PerformanceSeat> performanceSeats,
        PerformanceSaleSnapshot saleSnapshot
) {
}
