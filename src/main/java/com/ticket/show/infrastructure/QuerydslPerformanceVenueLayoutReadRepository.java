package com.ticket.show.infrastructure;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.PerformanceVenueLayoutReadRepository;
import com.ticket.show.domain.PerformanceVenueLayoutContext;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSeatLayout;
import com.ticket.venue.VenueSeatLookup;
import com.ticket.venue.VenueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.QPerformance.performance;
import static com.ticket.show.domain.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.QShow.show;

/**
 * {@link PerformanceVenueLayoutReadRepository}의 show 소유 구현이다. booking data를 참조하지
 * 않는다. 좌석 배치 좌표는 venue module의 {@link VenueSeatLookup}에서 조회한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceVenueLayoutReadRepository implements PerformanceVenueLayoutReadRepository {

    private final JPAQueryFactory queryFactory;
    private final VenueLookup venueLookup;
    private final VenueSeatLookup venueSeatLookup;

    @Override
    public Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(final long performanceId) {
        final Tuple row = queryFactory
                .select(performance.id, show.venueId)
                .from(performance)
                .join(show).on(show.id.eq(performance.showId))
                .where(performance.id.eq(performanceId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        final Long venueId = row.get(show.venueId);
        final VenueSummary venue = venueId == null ? null : venueLookup.findSummary(venueId).orElse(null);

        return Optional.of(new PerformanceVenueLayoutContext(
                row.get(performance.id),
                venueId,
                venue == null ? null : venue.name(),
                venue == null ? null : venue.seatMapLayout().viewBoxWidth(),
                venue == null ? null : venue.seatMapLayout().viewBoxHeight(),
                venue == null ? null : venue.seatMapLayout().seatDiameter()
        ));
    }

    @Override
    public List<SeatLayoutRow> findAllSeatLayouts(final long venueId) {
        return venueSeatLookup.findAllSeatLayouts(venueId).stream()
                .map(this::toSeatLayoutRow)
                .toList();
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
                .join(grade).on(grade.id.eq(performanceGrade.gradeId))
                .where(performanceGrade.performance.id.eq(performanceId))
                .fetch();
    }

    private SeatLayoutRow toSeatLayoutRow(final VenueSeatLayout layout) {
        return new SeatLayoutRow(
                layout.seatId(), layout.floor(), layout.section(), layout.rowNo(), layout.seatNo(),
                layout.x(), layout.y()
        );
    }
}
