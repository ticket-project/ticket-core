package com.ticket.booking.internal.application.performanceseat.query.model;

import java.math.BigDecimal;

public record SeatInfoView(
        Long seatId,
        int floor,
        String section,
        String row,
        String col,
        double x,
        double y,
        String gradeCode,
        String gradeName,
        BigDecimal price
) {
}
