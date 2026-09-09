package com.ticket.show.application;

import java.math.BigDecimal;

/**
 * 회차 하나의 grade 목록 화면 조회 결과다. {@code price}는 그 회차에서 확정된
 * {@code PerformanceGrade.price}이고, 같은 gradeCode라도 회차마다 다를 수 있다.
 */
public record PerformanceGradeView(
        Long performanceGradeId,
        String gradeCode,
        String gradeName,
        BigDecimal price,
        Integer sortOrder
) {
}
