package com.ticket.booking.infrastructure.order;

import com.ticket.booking.domain.order.model.OrderSeat;
import com.ticket.booking.domain.order.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link OrderSeatRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class OrderSeatRepositoryAdapter implements OrderSeatRepository {

    private final SpringDataOrderSeatJpaRepository jpaRepository;

    @Override
    public List<OrderSeat> saveAll(final List<OrderSeat> orderSeats) {
        return jpaRepository.saveAll(orderSeats);
    }

    @Override
    public List<OrderSeat> findAllByOrderIdOrderByIdAsc(final Long orderId) {
        return jpaRepository.findAllByOrder_IdOrderByIdAsc(orderId);
    }
}
