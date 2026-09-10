package com.ticket.show.performance.application.port;

import com.ticket.show.performance.domain.PerformanceSaleContext;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

/**
 * {@link com.ticket.show.PerformanceSaleCatalog}이 조회하는 회차 판매 표시값 포트다. show 자기
 * DB만 본다 — 좌석 주소 같은 venue 데이터는 여기 없다({@code PerformanceSaleCatalogService}가
 * venue module의 {@code VenueSeatLookup}을 직접 부른다).
 */
public interface PerformanceSaleQueryPort {

    Optional<PerformanceSaleContext> findContext(long performanceId);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade를 반환한다.
     */
    List<PerformanceGradeRow> findPerformanceGrades(long performanceId);

    record PerformanceGradeRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder,
            BigDecimal price
    ) {
    }
}
