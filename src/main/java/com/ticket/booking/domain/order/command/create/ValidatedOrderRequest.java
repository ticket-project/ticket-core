package com.ticket.booking.domain.order.command.create;

import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.PerformanceSaleSnapshot;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;

import java.util.List;

/**
 * 주문 생성 전 검증 결과. catalog 공개 정책 snapshot·표시 snapshot과 booking local 좌석 조회 결과를
 * 함께 전달해 이후 단계가 같은 값을 다시 조회하지 않게 한다.
 */
public record ValidatedOrderRequest(
        BookingPolicySnapshot policy,
        List<PerformanceSeat> performanceSeats,
        PerformanceSaleSnapshot saleSnapshot
) {
}
