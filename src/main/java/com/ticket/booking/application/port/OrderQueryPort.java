package com.ticket.booking.application.port;

import java.util.List;
import java.util.Optional;

import com.ticket.booking.application.query.OrderDetailRow;
import com.ticket.booking.application.query.OrderStatusView;

public interface OrderQueryPort {
    List<OrderDetailRow> findDetailRows(String orderKey, Long memberId);

    Optional<OrderStatusView> findStatus(String orderKey, Long memberId);
}
