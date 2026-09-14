package com.ticket.booking.infrastructure;

import static com.ticket.booking.domain.seat.QPerformanceSeat.performanceSeat;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.application.SeatStateSnapshotRow;
import com.ticket.booking.application.SeatStatus;
import com.ticket.booking.application.port.SeatStateQueryPort;
import com.ticket.booking.domain.seat.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatStateQueryPort implements SeatStateQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<SeatStateSnapshotRow> findSeatStates(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                SeatStateRow.class,
                                performanceSeat.id,
                                performanceSeat.seatId,
                                performanceSeat.state))
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
