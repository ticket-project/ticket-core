package com.ticket.booking.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ticket.booking.domain.order.Order;
import com.ticket.booking.domain.order.OrderKeyGenerator;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * PENDING 주문 aggregate를 조립한다. <b>저장하지 않는다</b> — 저장과 트랜잭션 경계는 {@link
 * CreatePendingOrderTransactionService}가 소유한다. 조립과 저장을 한 클래스가 함께 들고 있으면 "어디까지가 한 트랜잭션인가"가 두 곳에
 * 흩어진다.
 *
 * <p>주문 생성 시점의 show 표시값을 Order/OrderSeat에 snapshot으로 남긴다(ADR 0005). 금액은 show 값이 아니라 오직 {@link
 * PerformanceSeat#getUnitPrice()}로만 계산한다 — 클라이언트가 보낸 가격도, show가 다시 계산한 가격도 받지 않는다. 총액은 여기서 따로 더하지
 * 않고 {@code Order.addOrderSeat}가 좌석 단가를 누적한다 — 총액과 좌석 합계가 어긋날 경로를 두지 않는다.
 *
 * <p>좌석은 Order aggregate 안의 자식이라 별도 Repository 없이 root에 담고, root를 저장할 때 {@code cascade = ALL}로 함께
 * 저장된다.
 */
@Component
@RequiredArgsConstructor
public class OrderCreator {
    private final OrderKeyGenerator orderKeyGenerator;

    public Order createPendingOrder(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot) {
        final Order order =
                new Order(
                        memberId,
                        performanceId,
                        orderKeyGenerator.generate(),
                        holdKey,
                        expiresAt,
                        saleSnapshot.showTitle(),
                        saleSnapshot.performanceStartTime(),
                        saleSnapshot.venueName());
        performanceSeats.forEach(seat -> addOrderSeat(order, seat, saleSnapshot));
        return order;
    }

    private void addOrderSeat(
            final Order order,
            final PerformanceSeat seat,
            final PerformanceSaleSnapshot saleSnapshot) {
        final PerformanceSaleSnapshot.SeatInfo seatInfo =
                saleSnapshot.seatInfoBySeatId().get(seat.getSeatId());
        if (seatInfo == null) {
            throw new IllegalStateException("좌석 표시값을 찾을 수 없습니다. seatId=" + seat.getSeatId());
        }
        final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                saleSnapshot.gradeInfoByPerformanceGradeId().get(seat.getPerformanceGradeId());
        if (gradeInfo == null) {
            throw new IllegalStateException(
                    "등급 표시값을 찾을 수 없습니다. performanceGradeId=" + seat.getPerformanceGradeId());
        }
        order.addOrderSeat(
                seat.getId(),
                seat.getSeatId(),
                seat.getUnitPrice(),
                gradeInfo.gradeCode(),
                gradeInfo.gradeName(),
                seatInfo.label());
    }
}
