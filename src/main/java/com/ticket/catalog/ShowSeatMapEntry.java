package com.ticket.catalog;

import java.math.BigDecimal;

/**
 * show 하나의 좌석 맵 한 좌석분 표시값이다. 물리 좌석 위치와 그 show에서의 등급·가격을 함께 담는다.
 * {@code Seat}/{@code ShowSeat}/{@code ShowGrade} JPA entity를 노출하지 않는다.
 */
public record ShowSeatMapEntry(
        long seatId,
        int floor,
        String section,
        String rowNo,
        String seatNo,
        double x,
        double y,
        String gradeCode,
        String gradeName,
        BigDecimal price,
        int gradeSortOrder
) {
}
