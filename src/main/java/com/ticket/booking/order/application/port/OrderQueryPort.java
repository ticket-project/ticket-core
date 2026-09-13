package com.ticket.booking.order.application.port;

import java.util.List;
import java.util.Optional;

import com.ticket.booking.order.application.OrderDetailRow;
import com.ticket.booking.order.application.OrderStatusView;

public interface OrderQueryPort {
    List<OrderDetailRow> findDetailRows(String orderKey, Long memberId);

    Optional<OrderStatusView> findStatus(String orderKey, Long memberId);
}
