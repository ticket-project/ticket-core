package com.ticket.booking.order.application;

import com.ticket.booking.order.domain.OrderKeyGenerator;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCreator {

    private final OrderRepository orderRepository;
    private final OrderKeyGenerator orderKeyGenerator;

    /**
     * 주문 생성 시점의 show 표시값을 Order/OrderSeat에 snapshot으로 남긴다(ADR 0005). 금액은
     * show 값이 아니라 오직 {@link PerformanceSeat#getUnitPrice()}로만 계산한다 — 클라이언트가
     * 보낸 가격도, show가 다시 계산한 가격도 받지 않는다.
     *
     * <p>좌석은 Order aggregate 안의 자식이라 별도 Repository 없이 root에 담고, 같은 트랜잭션의
     * flush에서 {@code cascade = ALL}로 함께 저장된다.
     */
    @Transactional
    public Order createPendingOrder(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot
    ) {
        return create(memberId, performanceId, holdKey, expiresAt, performanceSeats, saleSnapshot);
    }

    private Order create(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot
    ) {
        final BigDecimal totalAmount = sumTotalAmount(performanceSeats);
        final String orderKey = orderKeyGenerator.generate();
        final Order order = orderRepository.save(new Order(
                memberId, performanceId, orderKey, holdKey, totalAmount, expiresAt,
                saleSnapshot.showTitle(), saleSnapshot.performanceStartTime(), saleSnapshot.venueName()
        ));
        performanceSeats.forEach(seat -> addOrderSeat(order, seat, saleSnapshot));
        return order;
    }

    private BigDecimal sumTotalAmount(final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(PerformanceSeat::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addOrderSeat(final Order order, final PerformanceSeat seat, final PerformanceSaleSnapshot saleSnapshot) {
        final PerformanceSaleSnapshot.SeatInfo seatInfo = saleSnapshot.seatInfoBySeatId().get(seat.getSeatId());
        if (seatInfo == null) {
            throw new IllegalStateException("좌석 표시값을 찾을 수 없습니다. seatId=" + seat.getSeatId());
        }
        final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                saleSnapshot.gradeInfoByPerformanceGradeId().get(seat.getPerformanceGradeId());
        if (gradeInfo == null) {
            throw new IllegalStateException("등급 표시값을 찾을 수 없습니다. performanceGradeId=" + seat.getPerformanceGradeId());
        }
        order.addOrderSeat(
                seat.getId(), seat.getSeatId(), seat.getUnitPrice(),
                gradeInfo.gradeCode(), gradeInfo.gradeName(), seatInfo.label()
        );
    }
}
