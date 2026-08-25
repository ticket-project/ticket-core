package com.ticket.core.infra.performanceseat.query;

import com.ticket.core.app.performanceseat.query.SeatMapQueryRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.app.performanceseat.query.model.SeatStateView;
import com.ticket.core.app.performanceseat.query.model.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.core.domain.performanceseat.model.QPerformanceSeat.performanceSeat;
import static com.ticket.core.domain.seat.model.QSeat.seat;
import static com.ticket.core.domain.show.mapping.QShowGrade.showGrade;
import static com.ticket.core.domain.show.mapping.QShowSeat.showSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatMapQueryRepository implements SeatMapQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SeatInfoView> findShowSeats(final Long showId) {
        return queryFactory
                .select(Projections.constructor(SeatInfoView.class,
                        seat.id,
                        seat.floor,
                        seat.section,
                        seat.rowNo,
                        seat.seatNo,
                        seat.x,
                        seat.y,
                        showGrade.gradeCode,
                        showGrade.gradeName,
                        showGrade.price
                ))
                .from(showSeat)
                .join(seat).on(seat.id.eq(showSeat.seat.id))
                .join(showGrade).on(showGrade.id.eq(showSeat.showGrade.id))
                .where(showSeat.show.id.eq(showId))
                .orderBy(seat.floor.asc(), seat.section.asc(), seat.rowNo.asc(), seat.seatNo.asc())
                .fetch();
    }

    @Override
    public List<SeatStateView> findSeatStatuses(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(SeatStateRow.class,
                        performanceSeat.seat.id,
                        performanceSeat.state
                ))
                .from(performanceSeat)
                .where(performanceSeat.performance.id.eq(performanceId))
                .orderBy(performanceSeat.seat.id.asc())
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
