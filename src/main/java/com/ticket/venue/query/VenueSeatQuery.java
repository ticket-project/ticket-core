package com.ticket.venue.query;

import static com.ticket.venue.domain.QSeat.seat;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueSeatLookupApi}의 venue 소유 구현이다.
 *
 * <p>물리 좌석 조회는 venue 자기 DB 한 번으로 끝나므로 공개 계약을 이 조회가 직접 구현한다 — 사이에 위임만 하는 service를 두면 계약과 SQL 사이에
 * 읽을 것 없는 경유 지점이 하나 늘 뿐이다. 다른 module은 계속 {@code venue.api}의 interface만 본다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueSeatQuery implements VenueSeatLookupApi {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<VenueSeatAddress> findSeatAddresses(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(
                        Projections.constructor(
                                VenueSeatAddress.class,
                                seat.id,
                                seat.floor,
                                seat.section,
                                seat.rowNo,
                                seat.seatNo))
                .from(seat)
                .where(seat.venueId.eq(venueId), seat.id.in(seatIds))
                .fetch();
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                VenueSeatLayout.class,
                                seat.id,
                                seat.floor,
                                seat.section,
                                seat.rowNo,
                                seat.seatNo,
                                seat.x,
                                seat.y))
                .from(seat)
                .where(seat.venueId.eq(venueId))
                .fetch();
    }
}
