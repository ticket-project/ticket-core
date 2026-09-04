package com.ticket.catalog.internal.infrastructure.performance.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.internal.application.performance.query.PerformanceSaleReadRepository;
import com.ticket.catalog.internal.domain.performance.query.PerformanceSaleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.ticket.catalog.internal.domain.grade.QGrade.grade;
import static com.ticket.catalog.internal.domain.performance.QPerformance.performance;
import static com.ticket.catalog.internal.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.catalog.internal.domain.seat.QSeat.seat;
import static com.ticket.catalog.internal.domain.show.QShow.show;
import static com.ticket.catalog.internal.domain.show.QVenue.venue;

/**
 * {@link PerformanceSaleReadRepository}의 catalog 소유 구현이다. booking data를 참조하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSaleReadRepository implements PerformanceSaleReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PerformanceSaleContext> findContext(final long performanceId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(PerformanceSaleContext.class,
                        performance.id,
                        show.id,
                        show.title,
                        venue.id,
                        venue.name,
                        performance.startTime
                ))
                .from(performance)
                .join(show).on(show.eq(performance.show))
                .leftJoin(venue).on(venue.eq(show.venue))
                .where(performance.id.eq(performanceId))
                .fetchOne());
    }

    @Override
    public List<SeatAddressRow> findSeatAddresses(final long venueId, final Collection<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(Projections.constructor(SeatAddressRow.class,
                        seat.id,
                        seat.floor,
                        seat.section,
                        seat.rowNo,
                        seat.seatNo
                ))
                .from(seat)
                .where(
                        seat.venue.id.eq(venueId),
                        seat.id.in(seatIds)
                )
                .fetch();
    }

    @Override
    public List<PerformanceGradeRow> findPerformanceGrades(final long performanceId) {
        return queryFactory
                .select(Projections.constructor(PerformanceGradeRow.class,
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
}
