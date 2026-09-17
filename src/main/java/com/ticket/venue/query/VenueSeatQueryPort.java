package com.ticket.venue.query;

import java.util.List;
import java.util.Set;

import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;

/** {@link com.ticket.venue.api.VenueSeatLookupApi}이 쓰는 물리 좌석 조회 포트다. */
public interface VenueSeatQueryPort {
    /** 빈 {@code seatIds}는 빈 목록을 반환한다. */
    List<VenueSeatAddress> findSeatAddresses(long venueId, Set<Long> seatIds);

    List<VenueSeatLayout> findAllSeatLayouts(long venueId);
}
