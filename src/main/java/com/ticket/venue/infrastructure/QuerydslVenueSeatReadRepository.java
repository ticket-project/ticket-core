package com.ticket.venue.infrastructure;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.venue.VenueSeatAddress;
import com.ticket.venue.VenueSeatLayout;
import com.ticket.venue.application.VenueSeatReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

import static com.ticket.venue.domain.QSeat.seat;

@Repository
@RequiredArgsConstructor
public class QuerydslVenueSeatReadRepository implements VenueSeatReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<VenueSeatAddress> findSeatAddresses(final long venueId, final Set<Long> seatIds) {
        return queryFactory
                .select(Projections.constructor(VenueSeatAddress.class,
                        seat.id, seat.floor, seat.section, seat.rowNo, seat.seatNo))
                .from(seat)
                .where(
                        seat.venueId.eq(venueId),
                        seat.id.in(seatIds)
                )
                .fetch();
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return queryFactory
                .select(Projections.constructor(VenueSeatLayout.class,
                        seat.id, seat.floor, seat.section, seat.rowNo, seat.seatNo, seat.x, seat.y))
                .from(seat)
                .where(seat.venueId.eq(venueId))
                .fetch();
    }
}
