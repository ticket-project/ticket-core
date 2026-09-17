package com.ticket.booking.order.query;

import java.util.List;
import java.util.Optional;

public interface OrderQueryPort {
    List<OrderDetailRow> findDetailRows(String orderKey, Long memberId);

    Optional<OrderStatusView> findStatus(String orderKey, Long memberId);
}
