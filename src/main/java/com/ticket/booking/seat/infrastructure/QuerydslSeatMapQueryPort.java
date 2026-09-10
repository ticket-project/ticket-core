package com.ticket.booking.seat.infrastructure;

import com.ticket.booking.seat.application.port.SeatMapQueryPort;

import com.ticket.booking.seat.application.port.SeatMapQueryPort;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.application.SeatStateSnapshotRow;
import com.ticket.booking.seat.application.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatMapQueryPort implements SeatMapQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SeatStateSnapshotRow> findSeatStatuses(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(SeatStateRow.class,
                        performanceSeat.id,
                        performanceSeat.seatId,
                        performanceSeat.state
                ))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch()
                .stream()
                .map(SeatStateRow::toSnapshotRow)
                .toList();
    }

    public record SeatStateRow(Long performanceSeatId, Long seatId, PerformanceSeatState state) {

        private SeatStateSnapshotRow toSnapshotRow() {
            return new SeatStateSnapshotRow(performanceSeatId, seatId, SeatStatus.from(state));
        }
    }
}
