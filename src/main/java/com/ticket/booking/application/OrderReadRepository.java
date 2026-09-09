package com.ticket.booking.application;

import com.ticket.booking.application.OrderDetailRow;
import com.ticket.booking.application.OrderStatusView;

import java.util.List;
import java.util.Optional;

public interface OrderReadRepository {

    List<OrderDetailRow> findDetailRows(String orderKey, Long memberId);

    Optional<OrderStatusView> findStatus(String orderKey, Long memberId);
}
