package com.ticket.core.domain.order.query.model;

import com.ticket.core.domain.order.model.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDetailRow(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt,
        Long showId,
        String showTitle,
        String showImageUrl,
        Long performanceId,
        Long performanceNo,
        LocalDateTime startTime,
        String venueName,
        Long memberId,
        String memberName,
        String memberEmail,
        LocalDateTime memberDeletedAt,
        Long performanceSeatId,
        Long seatId,
        int floor,
        String section,
        String rowNo,
        String seatNo,
        BigDecimal price
) {
}
