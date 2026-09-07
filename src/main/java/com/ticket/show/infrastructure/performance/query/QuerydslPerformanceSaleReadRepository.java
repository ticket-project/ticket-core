package com.ticket.show.infrastructure.performance.query;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.performance.query.PerformanceSaleReadRepository;
import com.ticket.show.domain.performance.query.PerformanceSaleContext;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSeatAddress;
import com.ticket.venue.VenueSeatLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.ticket.show.domain.grade.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;

/**
 * {@link PerformanceSaleReadRepository}의 show 소유 구현이다. booking data를 참조하지 않는다.
 * 좌석 주소는 venue module의 {@link VenueSeatLookup}에서 조회한다 — show는 물리 좌석 entity를
 * 참조하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSaleReadRepository implements PerformanceSaleReadRepository {

    private final JPAQueryFactory queryFactory;
    private final VenueLookup venueLookup;
    private final VenueSeatLookup venueSeatLookup;

    @Override
    public Optional<PerformanceSaleContext> findContext(final long performanceId) {
        final Tuple row = queryFactory
                .select(performance.id, show.id, show.title, show.venueId, performance.startTime)
                .from(performance)
                .join(show).on(show.eq(performance.show))
                .where(performance.id.eq(performanceId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        final Long venueId = row.get(show.venueId);
        final String venueName = venueId == null
                ? null
                : venueLookup.findSummary(venueId).map(v -> v.name()).orElse(null);

        return Optional.of(new PerformanceSaleContext(
                row.get(performance.id),
                row.get(show.id),
                row.get(show.title),
                venueId,
                venueName,
                row.get(performance.startTime)
        ));
    }

    @Override
    public List<SeatAddressRow> findSeatAddresses(final long venueId, final Collection<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return venueSeatLookup.findSeatAddresses(venueId, Set.copyOf(seatIds)).stream()
                .map(this::toSeatAddressRow)
                .toList();
    }

    @Override
    public List<PerformanceGradeRow> findPerformanceGrades(final long performanceId) {
        return queryFactory
                .select(com.querydsl.core.types.Projections.constructor(PerformanceGradeRow.class,
                        performanceGrade.id,
                        grade.code,
                        grade.name,
                        performanceGrade.sortOrder,
                        performanceGrade.price
                ))
                .from(performanceGrade)
                .join(grade).on(grade.eq(performanceGrade.grade))
                .where(performanceGrade.performance.id.eq(performanceId))
                .fetch();
    }

    private SeatAddressRow toSeatAddressRow(final VenueSeatAddress address) {
        return new SeatAddressRow(address.seatId(), address.floor(), address.section(), address.rowNo(), address.seatNo());
    }
}
