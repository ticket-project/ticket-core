package com.ticket.booking.internal.infrastructure.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.SeatMapReadRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateView;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.internal.domain.performanceseat.model.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatMapReadRepository implements SeatMapReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SeatStateView> findSeatStatuses(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(SeatStateRow.class,
                        performanceSeat.seatId,
                        performanceSeat.state
                ))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch()
                .stream()
                .map(SeatStateRow::toView)
                .toList();
    }

    public record SeatStateRow(Long seatId, PerformanceSeatState state) {

        private SeatStateView toView() {
            return new SeatStateView(seatId, SeatStatus.from(state));
        }
    }
}
