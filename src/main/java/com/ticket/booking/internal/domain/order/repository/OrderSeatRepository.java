package com.ticket.booking.internal.domain.order.repository;

import com.ticket.booking.internal.domain.order.model.OrderSeat;

import java.util.List;

/**
 * 주문 좌석 라인아이템의 저장과 복원을 담당하는 도메인 Repository다.
 */
public interface OrderSeatRepository {

    List<OrderSeat> saveAll(List<OrderSeat> orderSeats);

    List<OrderSeat> findAllByOrderIdOrderByIdAsc(Long orderId);
}
