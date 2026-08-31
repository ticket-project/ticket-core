package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.model.OrderSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataOrderSeatJpaRepository extends JpaRepository<OrderSeat, Long> {

    List<OrderSeat> findAllByOrder_IdOrderByIdAsc(Long orderId);
}
