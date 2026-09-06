package com.ticket.catalog.infrastructure.performance.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.application.performance.query.PerformanceVenueLayoutReadRepository;
import com.ticket.catalog.domain.performance.query.PerformanceVenueLayoutContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ticket.catalog.domain.grade.QGrade.grade;
import static com.ticket.catalog.domain.performance.QPerformance.performance;
import static com.ticket.catalog.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.catalog.domain.seat.QSeat.seat;
import static com.ticket.catalog.domain.show.QShow.show;
import static com.ticket.catalog.domain.show.QVenue.venue;

/**
 * {@link PerformanceVenueLayoutReadRepository}의 catalog 소유 구현이다. booking data를 참조하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceVenueLayoutReadRepository implements PerformanceVenueLayoutReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(final long performanceId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(PerformanceVenueLayoutContext.class,
                        performance.id,
                        venue.id,
                        venue.name,
                        venue.viewBoxWidth,
                        venue.viewBoxHeight,
                        venue.seatDiameter
                ))
                .from(performance)
                .join(show).on(show.eq(performance.show))
                .leftJoin(venue).on(venue.eq(show.venue))
                .where(performance.id.eq(performanceId))
                .fetchOne());
    }

    @Override
    public List<SeatLayoutRow> findAllSeatLayouts(final long venueId) {
        return queryFactory
                .select(Projections.constructor(SeatLayoutRow.class,
                        seat.id,
                        seat.floor,
                        seat.section,
                        seat.rowNo,
                        seat.seatNo,
                        seat.x,
                        seat.y
                ))
                .from(seat)
                .where(seat.venue.id.eq(venueId))
                .fetch();
    }

    @Override
    public List<PerformanceGradeLayoutRow> findGradeLayouts(final long performanceId) {
        return queryFactory
                .select(Projections.constructor(PerformanceGradeLayoutRow.class,
                        performanceGrade.id,
                        grade.code,
                        grade.name,
                        performanceGrade.sortOrder
                ))
                .from(performanceGrade)
                .join(grade).on(grade.eq(performanceGrade.grade))
                .where(performanceGrade.performance.id.eq(performanceId))
                .fetch();
    }
}
