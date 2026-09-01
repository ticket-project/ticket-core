package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicySnapshot;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;

import java.util.List;

/**
 * 주문 생성 전 DB 검증 결과. 한 읽기 트랜잭션에서 조회한 값을 함께 전달해
 * 이후 단계가 같은 값을 다시 조회하지 않게 한다.
 */
public record ValidatedOrderRequest(
        PerformanceBookingPolicySnapshot policy,
        List<PerformanceSeat> performanceSeats
) {
}
