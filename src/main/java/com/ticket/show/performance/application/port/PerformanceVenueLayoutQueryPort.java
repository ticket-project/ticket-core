package com.ticket.show.performance.application.port;

import com.ticket.show.performance.domain.PerformanceVenueLayoutContext;

import java.util.List;
import java.util.Optional;

/**
 * {@link com.ticket.show.PerformanceVenueLayoutCatalog}이 조회하는 회차 정적 seat-map 표시값
 * 포트다. show 자기 DB만 본다 — venue 좌석 배치·seat-map 좌표는 여기 없다
 * ({@code PerformanceVenueLayoutCatalogService}가 venue module의 {@code VenueSeatLookup}을
 * 직접 부른다).
 */
public interface PerformanceVenueLayoutQueryPort {

    Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(long performanceId);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade의 표시값을 반환한다. 가격은 담지 않는다.
     */
    List<PerformanceGradeLayoutRow> findGradeLayouts(long performanceId);

    record PerformanceGradeLayoutRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder
    ) {
    }
}
