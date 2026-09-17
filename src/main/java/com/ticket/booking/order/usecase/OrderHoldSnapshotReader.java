package com.ticket.booking.order.usecase;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderSeat;

import lombok.RequiredArgsConstructor;

/**
 * 커밋 뒤 후속 처리가 쓸 주문 정보를 짧은 읽기 트랜잭션에서 값으로 완성한다.
 *
 * <p>별도 component인 이유는 self-invocation을 피하기 위해서다 — 같은 클래스 안에서 부르면 {@code @Transactional} proxy가
 * 적용되지 않아, 트랜잭션 없이 실행되는 listener에서 좌석 컬렉션을 읽다가 lazy 초기화에 실패한다.
 */
@Component
@RequiredArgsConstructor
public class OrderHoldSnapshotReader {
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Optional<OrderHoldSnapshot> read(final Long orderId) {
        return orderRepository
                .findById(orderId)
                .map(
                        order ->
                                new OrderHoldSnapshot(
                                        order.getPerformanceId(),
                                        seatIdsOf(order),
                                        order.getExpiresAt()));
    }

    private List<Long> seatIdsOf(final Order order) {
        return order.getOrderSeats().stream().map(OrderSeat::getSeatId).toList();
    }
}
