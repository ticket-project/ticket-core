package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.api.PerformanceSaleSnapshot.GradeInfo;
import com.ticket.show.api.PerformanceVenueLayout.GradeLayout;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;

import lombok.RequiredArgsConstructor;

/**
 * show 자기 DB에서 회차 표시값을 읽는다 — 회차 요약, 회차별 grade 목록, 판매 snapshot 조회({@link
 * com.ticket.show.api.PerformanceSaleCatalogApi}), 정적 seat-map 조회({@link
 * com.ticket.show.api.PerformanceVenueLayoutCatalogApi})가 한 곳에 있다.
 *
 * <p>회차 요약({@code GetPerformanceSummaryUseCase})도 판매 snapshot과 같은 {@link #findContext} 조회를 재사용한다 —
 * 같은 값을 읽는 조회를 두 벌 두지 않는다.
 *
 * <p>booking data도, venue data도 여기서 참조하지 않는다 — {@code venueId} scalar만 넘긴다. venue 이름·region·좌석
 * 주소·좌석 배치 좌표 조합은 application({@code GetPerformanceSummaryUseCase}, {@code
 * PerformanceSaleCatalogService}, {@code PerformanceVenueLayoutCatalogService})이 venue module의 공개
 * 계약을 직접 불러서 한다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceQueryRepository {
    private final SpringDataPerformanceJpaRepository performanceJpaRepository;

    public Optional<PerformanceSaleContext> findContext(final long performanceId) {
        return performanceJpaRepository.findSaleContextByPerformanceId(performanceId);
    }

    /** 이 회차에 배정된 모든 PerformanceGrade를 반환한다. */
    public List<GradeInfo> findPerformanceGrades(final long performanceId) {
        return performanceJpaRepository.findGradeInfosByPerformanceId(performanceId);
    }

    public Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(
            final long performanceId) {
        return performanceJpaRepository.findVenueLayoutContextByPerformanceId(performanceId);
    }

    public Optional<Long> findRepresentativePerformanceIdByShowId(final long showId) {
        return performanceJpaRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    /** 이 회차에 배정된 모든 PerformanceGrade의 표시값을 반환한다. 가격은 담지 않는다. */
    public List<GradeLayout> findGradeLayouts(final long performanceId) {
        return performanceJpaRepository.findGradeLayoutsByPerformanceId(performanceId);
    }
}
