package com.ticket.booking.application;

import java.util.List;

import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;

/**
 * 주문 생성 전 검증 결과. booking local 판매 정책·show 표시 snapshot과 booking local 좌석 조회 결과를 함께 전달해 이후 단계가 같은 값을
 * 다시 조회하지 않게 한다.
 */
public record ValidatedOrderContext(
        PerformanceSalesPolicy policy,
        List<PerformanceSeat> performanceSeats,
        PerformanceSaleSnapshot saleSnapshot) {}
