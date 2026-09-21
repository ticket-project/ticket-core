package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.api.PerformanceSaleSnapshot.GradeInfo;
import com.ticket.show.api.PerformanceVenueLayout.GradeLayout;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceRepository}의 JPA 구현이다.
 *
 * <p>booking data도, venue data도 여기서 참조하지 않는다 — {@code venueId} scalar만 넘긴다. venue 이름·region·좌석
 * 주소·좌석 배치 좌표 조합은 application({@code GetPerformanceSummaryUseCase}, {@code
 * PerformanceSaleCatalogService}, {@code PerformanceVenueLayoutCatalogService})이 venue module의 공개
 * 계약을 직접 불러서 한다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceRepositoryAdapter implements PerformanceRepository {
    private final SpringDataPerformanceJpaRepository jpaRepository;

    @Override
    public List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(final Long showId) {
        return jpaRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId);
    }

    @Override
    public Optional<Performance> findById(final Long performanceId) {
        return jpaRepository.findById(performanceId);
    }

    @Override
    public Optional<PerformanceSaleContext> findSaleContext(final long performanceId) {
        return jpaRepository.findSaleContextByPerformanceId(performanceId);
    }

    @Override
    public Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(
            final long performanceId) {
        return jpaRepository.findVenueLayoutContextByPerformanceId(performanceId);
    }

    @Override
    public Optional<Long> findRepresentativePerformanceIdByShowId(final long showId) {
        return jpaRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    @Override
    public List<GradeInfo> findPerformanceGrades(final long performanceId) {
        return jpaRepository.findGradeInfosByPerformanceId(performanceId);
    }

    @Override
    public List<GradeLayout> findGradeLayouts(final long performanceId) {
        return jpaRepository.findGradeLayoutsByPerformanceId(performanceId);
    }
}
