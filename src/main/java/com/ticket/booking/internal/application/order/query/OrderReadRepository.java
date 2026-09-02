package com.ticket.booking.internal.application.order.query;

import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.application.order.query.model.OrderStatusView;

import java.util.List;
import java.util.Optional;

public interface OrderReadRepository {

    List<OrderDetailRow> findDetailRows(String orderKey, Long memberId);

    Optional<OrderStatusView> findStatus(String orderKey, Long memberId);
}
